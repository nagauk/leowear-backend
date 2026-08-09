package com.clothstore.controller;

import com.clothstore.dto.ApiResponse;
import com.clothstore.dto.OrderDto;
import com.clothstore.dto.OrderRequest;
import com.clothstore.entity.OrderStatus;
import com.clothstore.service.OrderPdfService;
import com.clothstore.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderPdfService orderPdfService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto>> placeOrder(
            @Valid @RequestBody OrderRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Order placed successfully",
                orderService.placeOrder(auth.getName(), request)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<OrderDto>>> myOrders(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.getMyOrders(auth.getName(), PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDto>> getOrder(
            @PathVariable Long id,
            Authentication auth) {
        boolean isAdmin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))
                || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.getOrderById(id, auth.getName(), isAdmin)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Page<OrderDto>>> allOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate fromDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate toDate) {
        java.time.LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        java.time.LocalDateTime to = toDate != null ? toDate.atTime(23, 59, 59) : null;
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.getAllOrdersFiltered(status, keyword, from, to, PageRequest.of(page, size))));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<OrderDto>> updateStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated",
                orderService.updateStatus(id, status)));
    }

    /**
     * Staff-only: mark order fully paid after COD remaining cash is collected at delivery.
     */
    @PatchMapping("/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<OrderDto>> markFullyPaid(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Marked fully paid",
                orderService.markFullyPaid(id)));
    }

    /** Staff-only: set courier / tracking / AWB info on a confirmed order. */
    @PutMapping("/{id}/shipping-details")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<OrderDto>> updateShippingDetails(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String details = body == null ? null : body.get("shippingDetails");
        return ResponseEntity.ok(ApiResponse.ok("Shipping details updated",
                orderService.updateShippingDetails(id, details)));
    }

    /**
     * Staff-only: PDF export of the current filtered orders. Mirrors the on-screen
     * Manage Orders filters (keyword, status, payment chip, date range).
     */
    @GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<byte[]> exportOrdersPdf(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String payment) {

        OrderPdfService.Filter f = new OrderPdfService.Filter(status, keyword, fromDate, toDate, payment);
        byte[] pdf = orderPdfService.buildOrdersPdf(f);
        String filename = orderPdfService.suggestedFilename(LocalDateTime.now());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdf.length);
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(pdf, headers, org.springframework.http.HttpStatus.OK);
    }

    /**
     * Staff-only: download single-order invoice PDF (A6).
     * Invoice number = Order number. Contains full customer + shipping details
     * for packing and courier use.
     */
    @GetMapping(value = "/{id}/invoice", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable Long id) {
        OrderPdfService.InvoicePdf invoice = orderPdfService.buildInvoicePdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", invoice.filename());
        headers.setContentLength(invoice.bytes().length);
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(invoice.bytes(), headers, org.springframework.http.HttpStatus.OK);
    }
}
