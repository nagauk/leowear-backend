package com.clothstore.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductDto {
    private Long id;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;

    private BigDecimal originalPrice;

    @Min(0)
    private Integer stock;

    private String brand;
    private String material;
    private String features;
    private String color;
    private String size;

    private String imageUrl;
    private List<ProductImageDto> images = new ArrayList<>();

    /** Write: image list with optional color per image */
    private List<ProductImageDto> imageList;

    /** Simple URL list (backward compat) */
    private List<String> imageUrls;
    private Integer primaryImageIndex;

    /** Size/color stock variants */
    private List<ProductVariantDto> variants = new ArrayList<>();

    /** Distinct sizes available (derived) */
    private List<String> availableSizes = new ArrayList<>();
    /** Distinct colors available (derived) */
    private List<String> availableColors = new ArrayList<>();

    private Long categoryId;
    private String categoryName;
    private Long parentCategoryId;
    private String parentCategoryName;
    private String sizeGuide;
    private Boolean active = true;
}
