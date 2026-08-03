package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_variants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_size_color", columnNames = {"product_id", "size", "color"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 30)
    private String size;

    @Column(nullable = false, length = 40)
    private String color;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    /** Optional price override; null = use product base price */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(length = 50)
    private String sku;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
