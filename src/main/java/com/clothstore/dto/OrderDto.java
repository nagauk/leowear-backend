package com.clothstore.dto;

import com.clothstore.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderDto {
    private Long id;
    private String orderNumber;
    private Long userId;
    private String username;
    private List<OrderItemDto> items;
    private BigDecimal subtotal;
    private BigDecimal deliveryCharge;
    private BigDecimal totalAmount;
    private String pincode;
    private OrderStatus status;
    private String shippingAddress;
    private String phone;
    private String notes;
    /** Staff-only shipping details (courier / tracking / AWB). Empty string for non-staff. */
    private String shippingDetails;
    private String paymentMethod;
    private String paymentStatus;
    private String paymentRef;
    /** true when customer still needs to pay (COD unpaid or prepaid pending) */
    private Boolean needsPayment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class OrderItemDto {
        private Long id;
        private Long productId;
        private Long variantId;
        private String productName;
        private String productImage;
        private String size;
        private String color;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}
