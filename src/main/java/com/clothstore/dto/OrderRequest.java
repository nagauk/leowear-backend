package com.clothstore.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class OrderRequest {
    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;

    private String shippingAddress;
    private String phone;
    private String notes;
    private String pincode;

    /** COD or PREPAID */
    private String paymentMethod;

    /**
     * Optional coupon code. Validated server-side at order-placement time
     * (not at this DTO parsing layer). Codes are normalised to upper-case.
     */
    private String couponCode;

    @Data
    public static class OrderItemRequest {
        @NotNull
        private Long productId;

        /** Preferred: exact variant */
        private Long variantId;

        private String size;
        private String color;

        @NotNull
        private Integer quantity;
    }
}
