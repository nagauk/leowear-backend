package com.clothstore.service;

import com.clothstore.dto.CategoryDto;
import com.clothstore.entity.Category;
import com.clothstore.exception.ResourceNotFoundException;
import com.clothstore.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryDto> getAll() {
        return categoryRepository.findAll().stream().map(this::toDtoFlat).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getTree() {
        return categoryRepository.findRootsWithChildren().stream()
                .map(this::toDtoTree)
                .collect(Collectors.toList());
    }

    public CategoryDto getById(Long id) {
        return toDtoTree(getEntity(id));
    }

    public Category getEntity(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    @Transactional
    public CategoryDto create(CategoryDto dto) {
        Category parent = null;
        if (dto.getParentId() != null) {
            parent = getEntity(dto.getParentId());
        }
        Category category = Category.builder()
                .name(dto.getName().trim())
                .description(dto.getDescription())
                .parent(parent)
                .sizeGuide(dto.getSizeGuide() != null ? dto.getSizeGuide() : inferSizeGuide(dto.getName(), parent))
                .build();
        return toDtoFlat(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto update(Long id, CategoryDto dto) {
        Category category = getEntity(id);
        if (dto.getName() != null) category.setName(dto.getName().trim());
        if (dto.getDescription() != null) category.setDescription(dto.getDescription());
        if (dto.getSizeGuide() != null) category.setSizeGuide(dto.getSizeGuide());
        if (dto.getParentId() != null) {
            category.setParent(getEntity(dto.getParentId()));
        }
        return toDtoFlat(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found: " + id);
        }
        categoryRepository.deleteById(id);
    }

    private String inferSizeGuide(String name, Category parent) {
        String n = (name + " " + (parent != null ? parent.getName() : "")).toLowerCase();
        if (n.contains("pant") || n.contains("jean") || n.contains("trouser") || n.contains("short")) return "PANTS";
        if (n.contains("kid") || n.contains("child")) return "KIDS";
        if (n.contains("access") || n.contains("belt") || n.contains("bag")) return "ACCESSORY";
        if (n.contains("shoe") || n.contains("footwear")) return "FOOTWEAR";
        return "APPAREL";
    }

    private CategoryDto toDtoFlat(Category c) {
        return CategoryDto.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                .parentName(c.getParent() != null ? c.getParent().getName() : null)
                .sizeGuide(c.getSizeGuide())
                .build();
    }

    private CategoryDto toDtoTree(Category c) {
        CategoryDto dto = toDtoFlat(c);
        if (c.getChildren() != null) {
            dto.setChildren(c.getChildren().stream().map(this::toDtoFlat).collect(Collectors.toList()));
        }
        return dto;
    }
}
