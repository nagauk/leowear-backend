package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Flat delivery charge applied when subtotal is below free threshold */
    @Column(name = "delivery_charge", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal deliveryCharge = new BigDecimal("49.00");

    /** Orders at or above this amount get free delivery */
    @Column(name = "free_delivery_min", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal freeDeliveryMinAmount = new BigDecimal("999.00");

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
