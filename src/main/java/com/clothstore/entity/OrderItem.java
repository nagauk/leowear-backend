package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(length = 30)
    private String size;

    @Column(length = 40)
    private String color;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Snapshot of the product's pre-discount MRP at order-placement time.
     * Null when the product had no MRP (or MRP ≤ price). Used by the
     * invoice / order-history breakdown to show "saved on this product"
     * even after the catalog's {@code originalPrice} is later changed.
     */
    @Column(name = "original_unit_price", precision = 10, scale = 2)
    private BigDecimal originalUnitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
