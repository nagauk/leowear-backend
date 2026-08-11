package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Discount coupon managed by ADMIN only. Customers can submit a code at
 * checkout-time via {@code POST /api/coupons/validate}; validation never
 * exposes this row directly — it returns a {@link com.clothstore.dto.CouponValidationDto}
 * with only the public fields.
 *
 * <p>Discounts are <strong>percentage only</strong> ({@code discountPercent}):
 * applies to cart subtotal (items total before delivery), not to delivery.
 * This keeps free-delivery thresholds intact.</p>
 */
@Entity
@Table(name = "coupons", indexes = {
        @Index(name = "idx_coupon_code", columnList = "code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored uppercased; unique across the table. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(length = 200)
    private String description;

    /** 1–100. Applied to cart subtotal (items, not delivery). */
    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /** Optional: minimum cart subtotal required to redeem. */
    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    /** Optional expiry. {@code null} = never expires. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Optional global usage cap. {@code null} = unlimited. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    /** Optional per-user usage cap. {@code null} = unlimited. */
    @Column(name = "usage_per_user")
    private Integer usagePerUser;

    /** When true, only customers who have no prior orders may redeem. */
    @Column(name = "first_time_user_only", nullable = false)
    @Builder.Default
    private Boolean firstTimeUserOnly = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Cached counter for fast UI display. Authoritative usage count comes from
     * {@link CouponRedemption} rows; this is incremented in the same transaction
     * as the redemption insert.
     */
    @Column(name = "times_used", nullable = false)
    @Builder.Default
    private Integer timesUsed = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (firstTimeUserOnly == null) firstTimeUserOnly = false;
        if (active == null) active = true;
        if (timesUsed == null) timesUsed = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}