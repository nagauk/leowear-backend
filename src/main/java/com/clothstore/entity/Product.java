package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    /** Aggregate stock (sum of variants when variants exist) */
    @Column(nullable = false)
    private Integer stock = 0;

    @Column(length = 50)
    private String brand;

    /** Fabric / material e.g. Cotton, Polyester, Denim */
    @Column(length = 100)
    private String material;

    /** Comma-separated features e.g. Stretchable, Slim Fit, Pencil Cut */
    @Column(length = 500)
    private String features;

    /** Default/display color when no variant selected */
    @Column(length = 30)
    private String color;

    /** Default/display size when no variant selected */
    @Column(length = 20)
    private String size;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    @OrderBy("primary DESC, sortOrder ASC, id ASC")
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private List<ProductVariant> variants = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private Boolean active = true;

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

    public void syncPrimaryImageUrl() {
        imageUrl = images.stream()
                .filter(ProductImage::isPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElseGet(() -> images.stream()
                        .min(Comparator.comparingInt(ProductImage::getSortOrder))
                        .map(ProductImage::getUrl)
                        .orElse(null));
    }

    public void recalculateStockFromVariants() {
        if (variants == null || variants.isEmpty()) return;
        stock = variants.stream()
                .filter(v -> Boolean.TRUE.equals(v.getActive()))
                .mapToInt(v -> v.getStock() != null ? v.getStock() : 0)
                .sum();
    }
}
