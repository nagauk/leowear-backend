package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 500)
    private String url;

    /** Primary image for the product (or for this size/color group) */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    /**
     * Color this image belongs to.
     * Null = applies to all colors (fallback).
     */
    @Column(length = 40)
    private String color;

    /**
     * Size this image belongs to.
     * Null = applies to all sizes for the given color (or general).
     * When customer selects size+color, images matching both are preferred,
     * then color-only, then general.
     */
    @Column(length = 30)
    private String size;
}
