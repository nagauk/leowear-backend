package com.clothstore.service;

import com.clothstore.dto.OrderDto;
import com.clothstore.entity.Order;
import com.clothstore.entity.OrderItem;
import com.clothstore.entity.OrderStatus;
import com.clothstore.entity.User;
import com.clothstore.exception.ResourceNotFoundException;
import com.clothstore.repository.OrderRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Renders a PDF of the staff-side filtered orders list using OpenPDF.
 *
 * <p>Designed as a packing / shipping report — staff need the customer's
 * full address and phone, every item with its variant (size / color) and
 * quantity, and the courier/tracking info in one place to pick, pack, and
 * ship. Cells auto-grow in height so long addresses and item lists render
 * in full with no truncation.</p>
 */
@Service
@RequiredArgsConstructor
public class OrderPdfService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");
    private static final DateTimeFormatter PRINT_TS = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("dd MMM yyyy");

    /** Landscape A4 usable width ≈ 770pt. Sum equals COLUMN_TOTAL. */
    private static final float[] COLUMN_WIDTHS = {
            56f,  // Order #
            64f,  // Customer
            160f, // Address (full, wraps)
            56f,  // Phone
            188f, // Items (full variant list, wraps)
            56f,  // Total
            62f,  // Payment
            46f,  // Status
            96f,  // Shipping details
            48f   // Date
    };
    private static final String[] HEADERS = {
            "Order #", "Customer", "Address", "Phone", "Items",
            "Total", "Payment", "Status", "Shipping Details", "Date"
    };

    /** Status colour accents — applied to the status cell so a printed sheet is glanceable. */
    private static final Color STATUS_BG_PENDING = new Color(0xfe, 0xf3, 0xc7);
    private static final Color STATUS_FG_PENDING = new Color(0x92, 0x40, 0x0e);
    private static final Color STATUS_BG_CONFIRMED = new Color(0xdb, 0xea, 0xfe);
    private static final Color STATUS_FG_CONFIRMED = new Color(0x1e, 0x40, 0xaf);
    private static final Color STATUS_BG_SHIPPED = new Color(0xe0, 0xe7, 0xff);
    private static final Color STATUS_FG_SHIPPED = new Color(0x37, 0x30, 0xa3);
    private static final Color STATUS_BG_DELIVERED = new Color(0xd1, 0xfa, 0xe5);
    private static final Color STATUS_FG_DELIVERED = new Color(0x06, 0x5f, 0x46);
    private static final Color STATUS_BG_CANCELLED = new Color(0xfe, 0xe2, 0xe2);
    private static final Color STATUS_FG_CANCELLED = new Color(0x99, 0x1b, 0x1b);
    private static final Color STATUS_BG_RETURNED = new Color(0xf3, 0xe8, 0xff);
    private static final Color STATUS_FG_RETURNED = new Color(0x6b, 0x21, 0xa8);

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /** Filter bundle handed in by the controller. Null fields mean "no filter". */
    public record Filter(
            OrderStatus status,
            String keyword,
            LocalDate fromDate,
            LocalDate toDate,
            /** "PAID" | "PENDING" | "COD" | "PREPAID" | null/empty. */
            String payment
    ) {}

    /** {@code orders_YYYY-MM-DD_HH-mm.pdf} */
    public String suggestedFilename(LocalDateTime now) {
        return "orders_" + now.format(FILE_TS) + ".pdf";
    }

    public byte[] buildOrdersPdf(Filter filter) {
        Filter f = filter == null ? new Filter(null, null, null, null, null) : filter;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Slightly tighter margins so long cells have more room to render in full.
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 56, 48);
        PdfWriter writer = PdfWriter.getInstance(doc, out);

        String statusStr = f.status() == null ? null : f.status().name();
        String kw = (f.keyword() != null && !f.keyword().isBlank()) ? f.keyword().trim() : null;
        LocalDateTime from = f.fromDate() == null ? null : f.fromDate().atStartOfDay();
        LocalDateTime to = f.toDate() == null ? null : f.toDate().atTime(23, 59, 59);

        List<Order> orders = orderRepository.findAllFiltered(statusStr, kw, from, to);
        List<Order> filtered = applyPaymentChip(orders, f.payment());

        // Warm-up the items collection so the order rows render fully on first pass.
        for (Order o : filtered) {
            if (o.getItems() != null) {
                o.getItems().size();
                for (var it : o.getItems()) {
                    if (it.getProduct() != null) it.getProduct().getName();
                }
            }
        }

        writer.setPageEvent(new FooterEvent(filterSummaryLine(f), filtered.size()));

        doc.open();

        addHeaderBlock(doc, f, filtered.size());

        PdfPTable table = new PdfPTable(HEADERS.length);
        table.setWidthPercentage(100f);
        table.setWidths(COLUMN_WIDTHS);
        // Repeat the header row on every page.
        table.setHeaderRows(1);
        // No fixed row height — let each row grow to fit its content.
        table.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        addHeaderCells(table);

        for (Order o : filtered) {
            addOrderRow(table, orderService.toDto(o, true));
        }

        if (filtered.isEmpty()) {
            addEmptyRow(table);
        }

        doc.add(table);
        doc.close();
        return out.toByteArray();
    }

    // ---- header / footer -------------------------------------------------

    private void addHeaderBlock(Document doc, Filter f, int total) {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(26, 26, 46));
        Paragraph title = new Paragraph("Manage Orders — Packing & Shipping Report", titleFont);
        title.setAlignment(Element.ALIGN_LEFT);
        title.setSpacingAfter(4f);
        doc.add(title);

        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(90, 90, 90));
        Paragraph meta = new Paragraph(
                "Generated: " + LocalDateTime.now().format(PRINT_TS), metaFont);
        meta.setSpacingAfter(2f);
        doc.add(meta);

        Paragraph filterPara = new Paragraph(filterSummaryLine(f), metaFont);
        filterPara.setSpacingAfter(2f);
        doc.add(filterPara);

        Font countFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(40, 40, 40));
        Paragraph count = new Paragraph(total + " order(s)", countFont);
        count.setSpacingAfter(10f);
        doc.add(count);
    }

    /** Footer with page number + filter summary, repeated on every page. */
    private static final class FooterEvent extends PdfPageEventHelper {
        private final String filterLine;
        private final int totalRows;

        FooterEvent(String filterLine, int totalRows) {
            this.filterLine = filterLine;
            this.totalRows = totalRows;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Font font = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(120, 120, 120));
            Rectangle box = document.getPageSize();
            float left = document.leftMargin();
            float right = box.getWidth() - document.rightMargin();
            float bottom = document.bottomMargin() - 16f;

            Paragraph filterPara = new Paragraph(filterLine + "    |    " + totalRows + " order(s)", font);
            Phrase page = new Phrase(
                    "Page " + writer.getPageNumber() + "    |    Leo Wear — Orders Export",
                    font);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                    filterPara, left, bottom, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    page, right, bottom, 0);
        }
    }

    // ---- table cells -----------------------------------------------------

    private void addHeaderCells(PdfPTable table) {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Color headerBg = new Color(26, 26, 46);
        for (String h : HEADERS) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setHorizontalAlignment(Element.ALIGN_LEFT);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(6f);
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColorBottom(new Color(80, 80, 100));
            table.addCell(cell);
        }
    }

    private void addEmptyRow(PdfPTable table) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, new Color(110, 110, 110));
        PdfPCell cell = new PdfPCell(new Phrase("No orders match the current filter.", font));
        cell.setColspan(HEADERS.length);
        cell.setPadding(10f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorder(Rectangle.TOP);
        cell.setBorderColorTop(new Color(220, 220, 220));
        table.addCell(cell);
    }

    private void addOrderRow(PdfPTable table, OrderDto d) {
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(40, 40, 40));
        Font mutedFont = FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(110, 110, 110));
        Font moneyFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(20, 20, 20));

        // Order #
        addTextCell(table, nullSafe(d.getOrderNumber()), bodyFont);

        // Customer
        addTextCell(table, nullSafe(d.getUsername()), bodyFont);

        // Address — full, no truncation. PdfPCell auto-wraps via Phrase.
        addTextCell(table, nullSafe(d.getShippingAddress()), bodyFont);

        // Phone
        addTextCell(table, nullSafe(d.getPhone()), bodyFont);

        // Items — multi-line list of variants. This is the packing list.
        addItemsCell(table, d, bodyFont, mutedFont);

        // Total
        String total = d.getTotalAmount() == null
                ? "—"
                : "Rs." + d.getTotalAmount().setScale(0, RoundingMode.HALF_UP).toPlainString();
        addTextCell(table, total, moneyFont, Element.ALIGN_RIGHT);

        // Payment
        String payment = composePayment(d.getPaymentStatus(), d.getPaymentMethod());
        addTextCell(table, payment, bodyFont);

        // Status — colour-coded pill so a printed sheet is glanceable.
        addStatusCell(table, d.getStatus());

        // Shipping details — full, no truncation.
        String shipping = nullSafe(d.getShippingDetails());
        if (shipping.isEmpty()) {
            addTextCell(table, "—", mutedFont);
        } else {
            addTextCell(table, shipping, bodyFont);
        }

        // Date
        String date = d.getCreatedAt() == null ? "" : d.getCreatedAt().toLocalDate().format(DATE_ONLY);
        addTextCell(table, date, bodyFont);
    }

    /**
     * Items cell renders one line per OrderItem with name + size/color + qty + price.
     * No truncation: cell height grows to fit the whole list. The product name is
     * bolded; variant and quantity lines are muted so the eye lands on the SKU shape.
     */
    private void addItemsCell(PdfPTable table, OrderDto d, Font bodyFont, Font mutedFont) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(5f);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        cell.setBorder(Rectangle.NO_BORDER);

        List<OrderDto.OrderItemDto> items = d.getItems();
        if (items == null || items.isEmpty()) {
            cell.addElement(new Phrase("—", mutedFont));
        } else {
            int n = 0;
            for (OrderDto.OrderItemDto it : items) {
                if (n++ > 0) {
                    // Thin separator between items.
                    Paragraph sep = new Paragraph(" ");
                    sep.setSpacingBefore(2f);
                    sep.setSpacingAfter(2f);
                    cell.addElement(sep);
                }

                String name = it.getProductName() == null ? "Item" : it.getProductName();
                Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(20, 20, 20));
                Paragraph nameP = new Paragraph(name, nameFont);
                nameP.setSpacingAfter(1f);
                cell.addElement(nameP);

                StringBuilder variant = new StringBuilder();
                if (notBlank(it.getSize())) variant.append("Size: ").append(it.getSize()).append("  ");
                if (notBlank(it.getColor())) variant.append("Color: ").append(it.getColor()).append("  ");
                if (variant.length() > 0) {
                    Paragraph varP = new Paragraph(variant.toString().trim(), mutedFont);
                    varP.setSpacingAfter(1f);
                    cell.addElement(varP);
                }

                String qty = it.getQuantity() == null ? "0" : it.getQuantity().toString();
                String price = it.getUnitPrice() == null
                        ? "—"
                        : "Rs." + it.getUnitPrice().setScale(0, RoundingMode.HALF_UP).toPlainString();
                Paragraph qtyP = new Paragraph("Qty " + qty + "  @  " + price, bodyFont);
                cell.addElement(qtyP);
            }
            // Footer: total quantity across all lines, useful for picking.
            int totalQty = items.stream()
                    .mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity())
                    .sum();
            Paragraph totalP = new Paragraph(
                    "Total " + totalQty + " pcs across " + items.size() + " SKU(s)",
                    mutedFont);
            totalP.setSpacingBefore(4f);
            cell.addElement(totalP);
        }
        table.addCell(cell);
    }

    private void addStatusCell(PdfPTable table, OrderStatus status) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(60, 60, 60));
        Color bg = new Color(240, 240, 240);
        Color fg = new Color(60, 60, 60);
        if (status != null) {
            switch (status) {
                case PENDING -> { bg = STATUS_BG_PENDING; fg = STATUS_FG_PENDING; }
                case CONFIRMED -> { bg = STATUS_BG_CONFIRMED; fg = STATUS_FG_CONFIRMED; }
                case SHIPPED -> { bg = STATUS_BG_SHIPPED; fg = STATUS_FG_SHIPPED; }
                case DELIVERED -> { bg = STATUS_BG_DELIVERED; fg = STATUS_FG_DELIVERED; }
                case CANCELLED -> { bg = STATUS_BG_CANCELLED; fg = STATUS_FG_CANCELLED; }
                case RETURNED -> { bg = STATUS_BG_RETURNED; fg = STATUS_FG_RETURNED; }
            }
        }
        Font colouredFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, fg);
        PdfPCell cell = new PdfPCell(new Phrase(status == null ? "—" : status.name(), colouredFont));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5f);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setMinimumHeight(18f);
        table.addCell(cell);
    }

    private void addTextCell(PdfPTable table, String text, Font font) {
        addTextCell(table, text, font, Element.ALIGN_LEFT);
    }

    private void addTextCell(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        cell.setPadding(5f);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
    }

    // ---- helpers ---------------------------------------------------------

    private String filterSummaryLine(Filter f) {
        if (f == null) return "Filters: none";
        StringBuilder sb = new StringBuilder("Filters: ");
        boolean any = false;
        if (f.status() != null) { sb.append("status=").append(f.status().name()); any = true; }
        if (f.keyword() != null && !f.keyword().isBlank()) {
            if (any) sb.append(", ");
            sb.append("search=\"").append(f.keyword().trim()).append('"');
            any = true;
        }
        if (f.fromDate() != null) {
            if (any) sb.append(", ");
            sb.append("from=").append(f.fromDate().format(DATE_ONLY));
            any = true;
        }
        if (f.toDate() != null) {
            if (any) sb.append(", ");
            sb.append("to=").append(f.toDate().format(DATE_ONLY));
            any = true;
        }
        if (f.payment() != null && !f.payment().isBlank()) {
            if (any) sb.append(", ");
            sb.append("payment=").append(f.payment().toUpperCase(Locale.ROOT));
            any = true;
        }
        return any ? sb.toString() : "Filters: none";
    }

    /** Translate the on-screen payment chip into paymentStatus / paymentMethod match. */
    private List<Order> applyPaymentChip(List<Order> orders, String chip) {
        if (chip == null || chip.isBlank()) return orders;
        String c = chip.trim().toUpperCase(Locale.ROOT);
        return orders.stream().filter(o -> {
            String ps = o.getPaymentStatus() == null ? "" : o.getPaymentStatus().name();
            String pm = o.getPaymentMethod() == null ? "" : o.getPaymentMethod().name();
            return switch (c) {
                case "PAID" -> "PAID".equals(ps);
                case "PENDING" -> !"PAID".equals(ps); // mirrors on-screen PENDING chip
                case "COD" -> "COD".equals(pm);
                case "PREPAID" -> "PREPAID".equals(pm);
                default -> true;
            };
        }).collect(Collectors.toList());
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String composePayment(String status, String method) {
        if (status == null && method == null) return "";
        StringBuilder sb = new StringBuilder();
        if (status != null) sb.append(status);
        if (method != null) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(method);
        }
        return sb.toString();
    }

    // =====================================================================
    // Single-order Invoice (A6) — for packing & shipping
    // =====================================================================

    private static final Color INK = new Color(30, 30, 40);
    private static final Color MUTED = new Color(100, 100, 110);
    private static final Color ACCENT = new Color(26, 26, 46);
    private static final Color LIGHT_BG = new Color(245, 245, 248);
    private static final Color LINE = new Color(210, 210, 220);

    /** Result of invoice generation: PDF bytes + suggested download filename. */
    public record InvoicePdf(byte[] bytes, String filename) {}

    /** {@code invoice_ORD-XXXXXXXX.pdf} */
    public String invoiceFilename(Order order) {
        String num = order.getOrderNumber() != null ? order.getOrderNumber() : ("id-" + order.getId());
        return "invoice_" + num + ".pdf";
    }

    /**
     * Build a compact A6 invoice PDF for one order.
     * Designed to be printed and used as packing slip / shipping document.
     * Invoice number = Order number.
     */
    @Transactional(readOnly = true)
    public InvoicePdf buildInvoicePdf(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Ensure items + product names are loaded
        if (order.getItems() != null) {
            order.getItems().size();
            for (OrderItem it : order.getItems()) {
                if (it.getProduct() != null) it.getProduct().getName();
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // A6 = 105 × 148 mm. Tight margins so content fits.
        Document doc = new Document(PageSize.A6, 10, 10, 12, 12);
        PdfWriter.getInstance(doc, out);
        doc.open();

       // addInvoiceHeader(doc);
        addInvoiceMeta(doc, order);
        addCustomerBlock(doc, order);
        addItemsTable(doc, order);
        addTotalsBlock(doc, order);
        addPaymentAndNotes(doc, order);
        addInvoiceFooter(doc);

        doc.close();
        return new InvoicePdf(out.toByteArray(), invoiceFilename(order));
    }

    private void addInvoiceHeader(Document doc) {
        Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, ACCENT);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED);

        Paragraph title = new Paragraph("LEO WEAR", brand);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(1f);
        doc.add(title);

        Paragraph addr = new Paragraph("Miyapur, Hyderabad", small);
        addr.setAlignment(Element.ALIGN_CENTER);
        addr.setSpacingAfter(0.5f);
        doc.add(addr);

        Paragraph phone = new Paragraph("Ph: 7989398156", small);
        phone.setAlignment(Element.ALIGN_CENTER);
        phone.setSpacingAfter(4f);
        doc.add(phone);

        // Divider
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100f);
        PdfPCell lc = new PdfPCell(new Phrase(" "));
        lc.setBorder(Rectangle.BOTTOM);
        lc.setBorderColor(LINE);
        lc.setBorderWidth(0.8f);
        lc.setFixedHeight(2f);
        lc.setPadding(0);
        line.addCell(lc);
        doc.add(line);
    }

    private void addInvoiceMeta(Document doc, Order order) {
        Font label = FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED);
        Font value = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, INK);
        Font invTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, ACCENT);


        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100f);
        meta.setWidths(new float[]{1f, 1f});

        String orderNo = nullSafe(order.getOrderNumber());
        String date = order.getCreatedAt() != null
                ? order.getCreatedAt().format(DATE_ONLY)
                : LocalDate.now().format(DATE_ONLY);

        meta.addCell(metaCell("Invoice # ", orderNo, label, value, Element.ALIGN_LEFT));
        meta.addCell(metaCell("Date", date, label, value, Element.ALIGN_RIGHT));

        /*String status = order.getStatus() != null ? order.getStatus().name() : "—";
        meta.addCell(metaCell("Order Status", status, label, value, Element.ALIGN_LEFT));
        meta.addCell(metaCell("", "", label, value, Element.ALIGN_RIGHT));*/

        doc.add(meta);
    }

    private PdfPCell metaCell(String lbl, String val, Font labelFont, Font valueFont, int align) {
        Paragraph p = new Paragraph();
        if (notBlank(lbl)) {
            p.add(new Phrase(lbl + "\n", labelFont));
        }
        p.add(new Phrase(nullSafe(val), valueFont));
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(1.5f);
        c.setHorizontalAlignment(align);
        return c;
    }

    private void addCustomerBlock(Document doc, Order order) {
        Font section = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, ACCENT);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 7, INK);
        Font muted = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, MUTED);

        User user = order.getUser();
        String customerName = "Customer";
        String email = "";
        String billing = "";
        if (user != null) {
            if (notBlank(user.getFullName())) customerName = user.getFullName();
            else if (notBlank(user.getUsername())) customerName = user.getUsername();
            email = nullSafe(user.getEmail());
            billing = nullSafe(user.getAddress());
        }

        String phone = notBlank(order.getPhone())
                ? order.getPhone()
                : (user != null ? nullSafe(user.getPhone()) : "");
        String shipAddr = nullSafe(order.getShippingAddress());
        if (!notBlank(billing)) billing = shipAddr; // fall back to shipping

        // Section title
        Paragraph shipTitle = new Paragraph("SHIP TO", section);
        shipTitle.setSpacingBefore(5f);
        shipTitle.setSpacingAfter(1.5f);
        doc.add(shipTitle);

        Paragraph nameP = new Paragraph(customerName, body);
        nameP.setSpacingAfter(0.5f);
        doc.add(nameP);

        if (notBlank(shipAddr)) {
            Paragraph a = new Paragraph(shipAddr, body);
            a.setSpacingAfter(0.5f);
            doc.add(a);
        }
        if (notBlank(phone)) {
            Paragraph p = new Paragraph("Phone: " + phone, muted);
            p.setSpacingAfter(0.5f);
            doc.add(p);
        }
        if (notBlank(email)) {
            Paragraph e = new Paragraph("Email: " + email, muted);
            e.setSpacingAfter(1f);
            doc.add(e);
        }

        // Bill To (only if different from ship)
        if (notBlank(billing) && !billing.equalsIgnoreCase(shipAddr)) {
            Paragraph billTitle = new Paragraph("BILL TO", section);
            billTitle.setSpacingBefore(2f);
            billTitle.setSpacingAfter(1f);
            doc.add(billTitle);
            Paragraph b = new Paragraph(billing, body);
            b.setSpacingAfter(2f);
            doc.add(b);
        }
    }

    private void addItemsTable(Document doc, Order order) {
        Font headF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6.5f, Color.WHITE);
        Font bodyF = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, INK);
        Font mutedF = FontFactory.getFont(FontFactory.HELVETICA, 6f, MUTED);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100f);
        table.setWidths(new float[]{3.2f, 0.8f, 1.1f, 1.2f});
        table.setSpacingBefore(3f);
        table.setSpacingAfter(2f);
        table.setHeaderRows(1);

        // Header
        String[] heads = {"Item", "Qty", "Rate", "Amt"};
        for (String h : heads) {
            PdfPCell c = new PdfPCell(new Phrase(h, headF));
            c.setBackgroundColor(ACCENT);
            c.setPadding(2.5f);
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(h.equals("Item") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            table.addCell(c);
        }

        List<OrderItem> items = order.getItems();
        if (items == null || items.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No items", mutedF));
            empty.setColspan(4);
            empty.setPadding(3f);
            empty.setBorder(Rectangle.NO_BORDER);
            table.addCell(empty);
        } else {
            boolean alt = false;
            for (OrderItem it : items) {
                String name = "Item";
                if (it.getProduct() != null && notBlank(it.getProduct().getName())) {
                    name = it.getProduct().getName();
                }
                StringBuilder variant = new StringBuilder();
                if (notBlank(it.getSize())) variant.append(it.getSize());
                if (notBlank(it.getColor())) {
                    if (variant.length() > 0) variant.append(" / ");
                    variant.append(it.getColor());
                }
                String itemText = name;
                if (variant.length() > 0) itemText += "\n" + variant;

                int qty = it.getQuantity() != null ? it.getQuantity() : 0;
                BigDecimal rate = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal amt = it.getSubtotal() != null
                        ? it.getSubtotal()
                        : rate.multiply(BigDecimal.valueOf(qty));

                Color bg = alt ? LIGHT_BG : Color.WHITE;
                alt = !alt;

                table.addCell(itemCell(itemText, bodyF, mutedF, Element.ALIGN_LEFT, bg));
                table.addCell(itemCell(String.valueOf(qty), bodyF, mutedF, Element.ALIGN_RIGHT, bg));
                table.addCell(itemCell(rupees(rate), bodyF, mutedF, Element.ALIGN_RIGHT, bg));
                table.addCell(itemCell(rupees(amt), bodyF, mutedF, Element.ALIGN_RIGHT, bg));
            }
        }
        doc.add(table);
    }

    private PdfPCell itemCell(String text, Font main, Font muted, int align, Color bg) {
        // First line bold-ish, rest muted if multi-line
        Phrase ph;
        if (text != null && text.contains("\n")) {
            String[] parts = text.split("\n", 2);
            ph = new Phrase();
            ph.add(new Phrase(parts[0] + "\n", main));
            ph.add(new Phrase(parts[1], muted));
        } else {
            ph = new Phrase(nullSafe(text), main);
        }
        PdfPCell c = new PdfPCell(ph);
        c.setBackgroundColor(bg);
        c.setPadding(2.2f);
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_TOP);
        return c;
    }

    private void addTotalsBlock(Document doc, Order order) {
        Font label = FontFactory.getFont(FontFactory.HELVETICA, 7, MUTED);
        Font value = FontFactory.getFont(FontFactory.HELVETICA, 7, INK);
        Font totalL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, ACCENT);
        Font totalV = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, ACCENT);

        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(55f);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.setWidths(new float[]{1.4f, 1f});
        t.setSpacingBefore(2f);

        BigDecimal sub = order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO;
        BigDecimal del = order.getDeliveryCharge() != null ? order.getDeliveryCharge() : BigDecimal.ZERO;
        BigDecimal tot = order.getTotalAmount() != null ? order.getTotalAmount() : sub.add(del);

        t.addCell(totalCell("Subtotal", label, Element.ALIGN_RIGHT));
        t.addCell(totalCell(rupees(sub), value, Element.ALIGN_RIGHT));
        t.addCell(totalCell("Delivery", label, Element.ALIGN_RIGHT));
        t.addCell(totalCell(rupees(del), value, Element.ALIGN_RIGHT));

        // Separator
        PdfPCell sepL = new PdfPCell(new Phrase(" "));
        sepL.setBorder(Rectangle.TOP);
        sepL.setBorderColor(LINE);
        sepL.setBorderWidth(0.6f);
        sepL.setPadding(1f);
        PdfPCell sepR = new PdfPCell(new Phrase(" "));
        sepR.setBorder(Rectangle.TOP);
        sepR.setBorderColor(LINE);
        sepR.setBorderWidth(0.6f);
        sepR.setPadding(1f);
        t.addCell(sepL);
        t.addCell(sepR);

        t.addCell(totalCell("TOTAL", totalL, Element.ALIGN_RIGHT));
        t.addCell(totalCell(rupees(tot), totalV, Element.ALIGN_RIGHT));

        doc.add(t);
    }

    private PdfPCell totalCell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(1.2f);
        c.setHorizontalAlignment(align);
        return c;
    }

    private void addPaymentAndNotes(Document doc, Order order) {
        Font section = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, ACCENT);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 7, INK);
        Font muted = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, MUTED);

        String method = order.getPaymentMethod() != null ? order.getPaymentMethod().name() : "—";
        String payStatus = order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "—";
        String ref = nullSafe(order.getPaymentRef());

        Paragraph payTitle = new Paragraph("PAYMENT", section);
        payTitle.setSpacingBefore(5f);
        payTitle.setSpacingAfter(1f);
        doc.add(payTitle);

        Paragraph pay = new Paragraph(method + "  ·  " + payStatus, body);
        pay.setSpacingAfter(0.5f);
        doc.add(pay);
        if (notBlank(ref)) {
            Paragraph r = new Paragraph("Ref: " + ref, muted);
            r.setSpacingAfter(1f);
            doc.add(r);
        }

        if (notBlank(order.getShippingDetails())) {
            Paragraph shipTitle = new Paragraph("SHIPPING / TRACKING", section);
            shipTitle.setSpacingBefore(3f);
            shipTitle.setSpacingAfter(1f);
            doc.add(shipTitle);
            Paragraph s = new Paragraph(order.getShippingDetails(), body);
            s.setSpacingAfter(1f);
            doc.add(s);
        }

        if (notBlank(order.getNotes())) {
            Paragraph notesTitle = new Paragraph("NOTES", section);
            notesTitle.setSpacingBefore(3f);
            notesTitle.setSpacingAfter(1f);
            doc.add(notesTitle);
            Paragraph n = new Paragraph(order.getNotes(), muted);
            n.setSpacingAfter(1f);
            doc.add(n);
        }
    }

    private void addInvoiceFooter(Document doc) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA, 6, MUTED);
        Paragraph p = new Paragraph("Thank you for shopping with Leo Wear!", f);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingBefore(6f);
        doc.add(p);
        Paragraph p2 = new Paragraph("Miyapur, Hyderabad. Ph: 7989398156", f);
        p2.setAlignment(Element.ALIGN_CENTER);
        p2.setSpacingAfter(0);
        doc.add(p2);
    }

    private static String rupees(BigDecimal amount) {
        if (amount == null) return "₹0";
        return "₹" + amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
