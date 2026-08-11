package com.clothstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Customer-facing coupon preview returned by {@code POST /api/coupons/validate}.
 * Includes the discount math so the cart can show a live total without exposing
 * admin-only fields (raw counters, expiry, limits).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidationDto {
    private String code;
    private String description;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;
    private BigDecimal finalTotal;
    /** Exposed only when the coupon expires — cart can show a "expires on" hint. */
    private LocalDateTime expiresAt;
}