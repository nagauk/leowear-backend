package com.clothstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private Long id;
    private String name;
    private String description;
    private Long parentId;
    private String parentName;
    private String sizeGuide;
    @Builder.Default
    private List<CategoryDto> children = new ArrayList<>();
}
