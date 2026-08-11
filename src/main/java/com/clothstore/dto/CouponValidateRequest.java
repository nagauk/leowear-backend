package com.clothstore.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Customer-side payload for {@code POST /api/coupons/validate}. The cart
 * passes the code it typed and its current subtotal; the server returns
 * the discount math preview.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidateRequest {
    @NotBlank
    private String code;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal subtotal;
}