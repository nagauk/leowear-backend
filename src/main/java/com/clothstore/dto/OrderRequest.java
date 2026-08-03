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
