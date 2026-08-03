package com.clothstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantDto {
    private Long id;
    private String size;
    private String color;
    private Integer stock;
    private BigDecimal price;
    private String sku;
    private Boolean active = true;
}
