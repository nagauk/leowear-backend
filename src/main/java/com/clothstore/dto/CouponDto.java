package com.clothstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin-facing representation of a coupon. Includes every internal field —
 * never returned by the customer-side {@code /validate} endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponDto {
    private Long id;
    private String code;
    private String description;
    private BigDecimal discountPercent;
    private BigDecimal minOrderAmount;
    private LocalDateTime expiresAt;
    private Integer usageLimit;
    private Integer usagePerUser;
    private Boolean firstTimeUserOnly;
    private Boolean active;
    private Integer timesUsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}