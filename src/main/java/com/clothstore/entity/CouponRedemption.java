package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per (coupon, order) redemption. Used to enforce per-user caps and
 * to compute authoritative usage counts independent of the {@link Coupon#timesUsed}
 * cache. {@code order_id} is unique so the same order can never redeem two coupons
 * (and the same coupon can never be redeemed twice by the same order).
 */
@Entity
@Table(name = "coupon_redemptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_redemption_order", columnNames = "order_id"),
        indexes = {
                @Index(name = "idx_redemption_coupon", columnList = "coupon_id"),
                @Index(name = "idx_redemption_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** Snapshot of the code at redemption time — survives coupon edits/deletes. */
    @Column(name = "coupon_code_snapshot", nullable = false, length = 20)
    private String couponCodeSnapshot;

    @Column(name = "redeemed_at", nullable = false)
    private LocalDateTime redeemedAt;

    @PrePersist
    protected void onCreate() {
        if (redeemedAt == null) redeemedAt = LocalDateTime.now();
    }
}