package com.clothstore.service;

import com.clothstore.entity.Role;

import com.clothstore.dto.OrderDto;
import com.clothstore.dto.OrderRequest;
import com.clothstore.entity.*;
import com.clothstore.exception.BadRequestException;
import com.clothstore.exception.ResourceNotFoundException;
import com.clothstore.repository.OrderRepository;
import com.clothstore.repository.ProductRepository;
import com.clothstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final StoreSettingsService storeSettingsService;

    @Transactional
    public OrderDto placeOrder(String username, OrderRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() == Role.ADMIN) {
            throw new BadRequestException("Admin accounts cannot place shop orders");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Order must have at least one item");
        }

        Order order = Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .user(user)
                .status(OrderStatus.PENDING)
                .shippingAddress(request.getShippingAddress() != null ? request.getShippingAddress() : user.getAddress())
                .phone(request.getPhone() != null ? request.getPhone() : user.getPhone())
                .notes(request.getNotes())
                .paymentMethod(resolvePaymentMethod(request.getPaymentMethod()))
                .paymentStatus(PaymentStatus.PENDING)
                .items(new ArrayList<>())
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            if (itemReq.getProductId() == null) {
                throw new BadRequestException("Invalid cart item: missing product id");
            }
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found (id=" + itemReq.getProductId()
                                    + "). Your cart may be outdated — please clear cart and add items again."));

            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new BadRequestException("Product is not available: " + product.getName());
            }

            ProductVariant variant = resolveVariant(product, itemReq);
            BigDecimal unitPrice;
            int availableStock;
            String size;
            String color;

            if (variant != null) {
                availableStock = variant.getStock() != null ? variant.getStock() : 0;
                unitPrice = variant.getPrice() != null ? variant.getPrice() : product.getPrice();
                size = variant.getSize();
                color = variant.getColor();
            } else {
                availableStock = product.getStock() != null ? product.getStock() : 0;
                unitPrice = product.getPrice();
                size = itemReq.getSize() != null ? itemReq.getSize() : product.getSize();
                color = itemReq.getColor() != null ? itemReq.getColor() : product.getColor();
            }

            if (availableStock < itemReq.getQuantity()) {
                String label = product.getName()
                        + (size != null ? " / " + size : "")
                        + (color != null ? " / " + color : "");
                throw new BadRequestException("Insufficient stock for: " + label + ". Available: " + availableStock);
            }

            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));

            // Pick image matching color if possible
            String imageUrl = product.getImageUrl();
            if (color != null && product.getImages() != null) {
                imageUrl = product.getImages().stream()
                        .filter(img -> color.equalsIgnoreCase(img.getColor()))
                        .map(ProductImage::getUrl)
                        .findFirst()
                        .orElse(product.getImageUrl());
            }

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .variant(variant)
                    .size(size)
                    .color(color)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build();

            order.getItems().add(item);
            total = total.add(subtotal);

            if (variant != null) {
                variant.setStock(variant.getStock() - itemReq.getQuantity());
                product.recalculateStockFromVariants();
            } else {
                product.setStock(product.getStock() - itemReq.getQuantity());
            }
            productRepository.save(product);
        }

        BigDecimal delivery = storeSettingsService.computeDeliveryCharge(total);
        order.setSubtotal(total);
        order.setDeliveryCharge(delivery);
        // COD: ₹99 advance online (part of total, not an extra fee); rest at delivery
        BigDecimal advance = BigDecimal.ZERO;
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            advance = new BigDecimal("99");
        }
        order.setPlatformCharge(advance); // advance amount (legacy column name)
        order.setTotalAmount(total.add(delivery)); // do NOT add advance on top of total
        order.setPaidAmount(BigDecimal.ZERO);
        if (request.getPincode() != null) {
            order.setPincode(request.getPincode());
        }
        // PREPAID and COD stay PENDING until first online payment is confirmed
        order.setPaymentStatus(PaymentStatus.PENDING);
        orderRepository.save(order);
        notifyCustomerStatus(order);
        // Place order runs as the customer — hide staff-only shipping details.
        return toDto(order, false);
    }

    private ProductVariant resolveVariant(Product product, OrderRequest.OrderItemRequest itemReq) {
        // Ensure variants loaded
        if (product.getVariants() == null) {
            return null;
        }
        product.getVariants().size();
        if (product.getVariants().isEmpty()) {
            return null;
        }
        if (itemReq.getVariantId() != null) {
            return product.getVariants().stream()
                    .filter(v -> v.getId().equals(itemReq.getVariantId()) && Boolean.TRUE.equals(v.getActive()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Variant not found for product: " + product.getName()));
        }
        if (itemReq.getSize() != null && itemReq.getColor() != null) {
            return product.getVariants().stream()
                    .filter(v -> Boolean.TRUE.equals(v.getActive())
                            && v.getSize().equalsIgnoreCase(itemReq.getSize())
                            && v.getColor().equalsIgnoreCase(itemReq.getColor()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "Size/color combination not available: " + itemReq.getSize() + " / " + itemReq.getColor()));
        }
        throw new BadRequestException("Please select size and color for: " + product.getName());
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> getMyOrders(String username, Pageable pageable) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Page<Order> page = orderRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        // Touch collections so mapping is safe
        page.getContent().forEach(o -> {
            if (o.getItems() != null) {
                o.getItems().forEach(i -> {
                    if (i.getProduct() != null) {
                        i.getProduct().getName();
                        if (i.getProduct().getImages() != null) i.getProduct().getImages().size();
                    }
                });
            }
        });
        // Customer view: hide staff-only shipping details
        return page.map(o -> toDto(o, false));
    }

    public OrderDto getOrderById(Long id, String username, boolean isAdmin) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (!isAdmin && !order.getUser().getUsername().equals(username)) {
            throw new BadRequestException("You cannot view this order");
        }
        return toDto(order, isAdmin);
    }

    public Page<OrderDto> getAllOrders(Pageable pageable) {
        Page<Order> page = orderRepository.findAllByOrderByCreatedAtDesc(pageable);
        // Native queries skip Hibernate's EAGER joins; warm the items so the
        // DTO mapper (and the on-screen Items column) sees real data.
        warmOrderGraph(page.getContent());
        return page.map(o -> toDto(o, true));
    }

    public Page<OrderDto> getAllOrdersFiltered(
            OrderStatus status,
            String keyword,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable) {
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        // Native query needs the status as a String so its JDBC type is unambiguous.
        String statusStr = status == null ? null : status.name();
        Page<Order> page = orderRepository.findFiltered(statusStr, kw, fromDate, toDate, pageable);
        // Native queries skip Hibernate's EAGER joins; warm the items so the
        // DTO mapper (and the on-screen Items column) sees real data.
        warmOrderGraph(page.getContent());
        return page.map(o -> toDto(o, true));
    }

    /**
     * Force-load the items + product associations for a batch of orders whose
     * items collection came back empty (e.g. because they were returned from a
     * native query, which doesn't honour {@code FetchType.EAGER}).
     *
     * <p>Done as an explicit JPQL fetch — relying on Hibernate's lazy load through
     * a detached entity is fragile (sometimes an empty PersistentBag, sometimes
     * a {@code LazyInitializationException}). The returned entities overwrite
     * the placeholder instances, so by the time {@link #toDto} runs, each
     * {@code Order} has a real {@code items} list.</p>
     */
    private void warmOrderGraph(java.util.List<Order> orders) {
        if (orders == null || orders.isEmpty()) return;
        java.util.List<Long> ids = new java.util.ArrayList<>(orders.size());
        for (Order o : orders) ids.add(o.getId());
        // Returns the same Order entities (same id, same persistence context row),
        // but now with their items + products eagerly fetched. We then copy the
        // hydrated items back onto the original entity references so callers see
        // a populated list when they map via toDto(...).
        java.util.Map<Long, Order> hydrated = new java.util.HashMap<>();
        for (Order h : orderRepository.findWithItemsByIds(ids)) {
            hydrated.put(h.getId(), h);
        }
        for (Order o : orders) {
            Order h = hydrated.get(o.getId());
            if (h != null && h.getItems() != null) {
                o.setItems(h.getItems());
            }
        }
    }

    @Transactional
    public OrderDto updateStatus(Long id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        if (status == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                if (item.getVariant() != null) {
                    ProductVariant v = item.getVariant();
                    v.setStock(v.getStock() + item.getQuantity());
                    item.getProduct().recalculateStockFromVariants();
                } else {
                    Product p = item.getProduct();
                    p.setStock(p.getStock() + item.getQuantity());
                }
                productRepository.save(item.getProduct());
            }
        }

        order.setStatus(status);
        Order saved = orderRepository.save(order);
        notifyCustomerStatus(saved);
        return toDto(saved, true);
    }

    /**
     * Staff-only: capture courier / tracking / AWB details for a CONFIRMED order.
     * Rejected if the order hasn't been confirmed yet (PENDING isn't ready to ship).
     */
    @Transactional
    public OrderDto updateShippingDetails(Long id, String shippingDetails) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        if (order.getStatus() == null || order.getStatus() == OrderStatus.PENDING
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.RETURNED) {
            throw new BadRequestException(
                    "Shipping details can only be set after the order is confirmed");
        }
        String trimmed = (shippingDetails == null) ? null : shippingDetails.trim();
        order.setShippingDetails(trimmed == null || trimmed.isEmpty() ? null : trimmed);
        Order saved = orderRepository.save(order);
        return toDto(saved, true);
    }

    private void notifyCustomerStatus(Order order) {
        try {
            if (order.getUser() == null || order.getUser().getEmail() == null) return;
            String name = order.getUser().getFullName() != null ? order.getUser().getFullName() : order.getUser().getUsername();
            String total = order.getTotalAmount() != null ? order.getTotalAmount().toPlainString() : null;
            emailService.sendOrderStatus(
                    order.getUser().getEmail(),
                    name,
                    order.getOrderNumber(),
                    order.getStatus() != null ? order.getStatus().name() : "UPDATED",
                    total
            );
        } catch (Exception e) {
            // never fail order update because of email
            org.slf4j.LoggerFactory.getLogger(OrderService.class)
                    .warn("Order status email failed for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    public OrderDto toDto(Order order) {
        return toDto(order, true);
    }

    public OrderDto toDto(Order order, boolean isStaff) {
        // isStaff is retained for future field-level differences; shippingDetails
        // (courier / tracking / AWB) is intentionally exposed to the order owner
        // so customers can track SHIPPED / DELIVERED orders on "My Orders".
        return OrderDto.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUser().getId())
                .username(order.getUser().getUsername())
                .items(order.getItems() == null ? java.util.List.of() : order.getItems().stream().map(item -> {
                    String image = null;
                    Long productId = null;
                    String productName = "Item";
                    try {
                        if (item.getProduct() != null) {
                            productId = item.getProduct().getId();
                            productName = item.getProduct().getName();
                            image = item.getProduct().getImageUrl();
                            if (item.getColor() != null && item.getProduct().getImages() != null) {
                                image = item.getProduct().getImages().stream()
                                        .filter(img -> img.getColor() != null
                                                && item.getColor().equalsIgnoreCase(img.getColor()))
                                        .map(ProductImage::getUrl)
                                        .findFirst()
                                        .orElse(item.getProduct().getImageUrl());
                            }
                        }
                    } catch (Exception ignored) { }
                    return OrderDto.OrderItemDto.builder()
                            .id(item.getId())
                            .productId(productId)
                            .variantId(item.getVariant() != null ? item.getVariant().getId() : null)
                            .productName(productName)
                            .productImage(image)
                            .size(item.getSize())
                            .color(item.getColor())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .subtotal(item.getSubtotal())
                            .build();
                }).toList())
                .subtotal(order.getSubtotal())
                .deliveryCharge(order.getDeliveryCharge())
                .platformCharge(order.getPlatformCharge() != null ? order.getPlatformCharge() : BigDecimal.ZERO)
                .totalAmount(order.getTotalAmount())
                .paidAmount(resolvePaidAmount(order))
                .remainingAmount(resolveRemainingAmount(order))
                .pincode(order.getPincode())
                .status(order.getStatus())
                .shippingAddress(order.getShippingAddress())
                .phone(order.getPhone())
                .notes(order.getNotes())
                .shippingDetails(order.getShippingDetails())
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null)
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                .paymentRef(order.getPaymentRef())
                .needsPayment(order.getPaymentStatus() != PaymentStatus.PAID
                        && order.getStatus() != OrderStatus.CANCELLED
                        && order.getStatus() != OrderStatus.RETURNED)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private BigDecimal resolvePaidAmount(Order order) {
        if (order.getPaidAmount() != null) {
            return order.getPaidAmount();
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID && order.getTotalAmount() != null) {
            return order.getTotalAmount();
        }
        if (order.getPaymentStatus() == PaymentStatus.PARTIAL) {
            BigDecimal platform = order.getPlatformCharge() != null ? order.getPlatformCharge() : BigDecimal.ZERO;
            return platform;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveRemainingAmount(Order order) {
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = resolvePaidAmount(order);
        BigDecimal remaining = total.subtract(paid);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    private PaymentMethod resolvePaymentMethod(String raw) {
        if (raw == null || raw.isBlank()) return PaymentMethod.COD;
        try {
            return PaymentMethod.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return PaymentMethod.COD;
        }
    }

    /**
     * Record a successful online payment.
     * <ul>
     *   <li>COD + PENDING → PARTIAL (₹99 advance collected; reduces remaining due)</li>
     *   <li>COD + PARTIAL → PAID (remaining balance collected)</li>
     *   <li>PREPAID → PAID (full amount)</li>
     * </ul>
     */
    @Transactional
    public OrderDto markPaid(Long orderId, String username, String paymentRef, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!isAdmin && !order.getUser().getUsername().equals(username)) {
            throw new BadRequestException("You cannot pay for this order");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return toDto(order, isAdmin);
        }

        String ref = paymentRef != null ? paymentRef : ("LW" + System.currentTimeMillis());
        order.setPaymentRef(ref);

        if (order.getPaymentMethod() == PaymentMethod.COD) {
            if (order.getPaymentStatus() == PaymentStatus.PARTIAL) {
                // Customer paid remaining balance online
                order.setPaidAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
                order.setPaymentStatus(PaymentStatus.PAID);
            } else {
                // First payment: COD advance (part of order total)
                BigDecimal advance = order.getPlatformCharge() != null
                        ? order.getPlatformCharge()
                        : new BigDecimal("99");
                if (advance.compareTo(BigDecimal.ZERO) <= 0) {
                    advance = new BigDecimal("99");
                }
                order.setPaidAmount(advance);
                order.setPaymentStatus(PaymentStatus.PARTIAL);
            }
        } else {
            order.setPaymentMethod(PaymentMethod.PREPAID);
            order.setPaidAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
            order.setPaymentStatus(PaymentStatus.PAID);
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        return toDto(orderRepository.save(order), isAdmin);
    }

    /**
     * Staff action: mark COD order fully paid after remaining cash is collected at delivery.
     */
    @Transactional
    public OrderDto markFullyPaid(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return toDto(order, true);
        }
        order.setPaidAmount(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
        order.setPaymentStatus(PaymentStatus.PAID);
        if (order.getPaymentRef() == null || order.getPaymentRef().isBlank()) {
            order.setPaymentRef("COD-CASH-" + System.currentTimeMillis());
        } else if (!order.getPaymentRef().contains("COD-CASH")) {
            order.setPaymentRef(order.getPaymentRef() + "+COD-CASH");
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
        }
        return toDto(orderRepository.save(order), true);
    }
}

