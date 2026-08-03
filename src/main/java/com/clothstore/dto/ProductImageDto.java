package com.clothstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageDto {
    private Long id;
    private String url;
    private boolean primary;
    private int sortOrder;
    /** Color this image belongs to; null = all colors */
    private String color;
    /** Size this image belongs to; null = all sizes */
    private String size;
}
