package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    /** Items total before delivery */
    @Column(name = "subtotal", precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "delivery_charge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryCharge = BigDecimal.ZERO;

    /**
     * COD online advance (₹99). Part of the order total — not an extra fee.
     * Zero for prepaid. After advance is paid: paidAmount = advance; remaining = total − advance.
     * DB column kept as platform_charge for compatibility.
     */
    @Column(name = "platform_charge", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal platformCharge = BigDecimal.ZERO;

    /** Grand total = subtotal + deliveryCharge (advance is deducted from this, not added). */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "pincode", length = 12)
    private String pincode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Free-form shipping details (courier, tracking #, AWB, etc.)
     * Captured by ADMIN / EMPLOYEE after the order is CONFIRMED.
     * Customer-facing view hides this; staff portals show it.
     */
    @Column(name = "shipping_details", length = 500)
    private String shippingDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "payment_ref", length = 80)
    private String paymentRef;

    /**
     * Amount already collected online (platform fee for COD, or full total for prepaid).
     * Remaining = totalAmount - paidAmount (due at delivery for COD PARTIAL).
     */
    @Column(name = "paid_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
