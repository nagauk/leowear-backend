package com.clothstore.service;

import com.clothstore.dto.ProductDto;
import com.clothstore.dto.ProductImageDto;
import com.clothstore.dto.ProductVariantDto;
import com.clothstore.entity.Category;
import com.clothstore.entity.Product;
import com.clothstore.entity.ProductImage;
import com.clothstore.entity.ProductVariant;
import com.clothstore.exception.BadRequestException;
import com.clothstore.exception.ResourceNotFoundException;
import com.clothstore.repository.CategoryRepository;
import com.clothstore.repository.OrderRepository;
import com.clothstore.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CategoryRepository categoryRepository;

    public Page<ProductDto> getAllActive(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable).map(this::toDto);
    }

    public Page<ProductDto> getByCategory(Long categoryId, Pageable pageable) {
        // Parent category (e.g. Men) includes all subcategory products (T-Shirts, Jeans, …)
        return productRepository.findActiveByCategoryIncludingChildren(categoryId, pageable).map(this::toDto);
    }

    public Page<ProductDto> search(String keyword, Pageable pageable) {
        return productRepository.searchActive(keyword, pageable).map(this::toDto);
    }

    public Page<ProductDto> searchInCategory(String keyword, Long categoryId, Pageable pageable) {
        return productRepository.searchActiveInCategory(keyword, categoryId, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ProductDto getById(Long id) {
        Product product = productRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        // Force-load collections (SUBSELECT) inside transaction
        product.getImages().size();
        product.getVariants().size();
        return toDto(product);
    }

    private static final long MIN_ORDERS_FOR_BESTSELLERS = 1000;

    @Transactional(readOnly = true)
    public List<ProductDto> getBestsellers(int limit) {
        // Show "Most Sold" only after the store has enough order history
        if (orderRepository.count() < MIN_ORDERS_FOR_BESTSELLERS) {
            return List.of();
        }
        int n = Math.max(1, Math.min(limit, 24));
        List<Product> list = productRepository.findBestsellers(n);
        list.forEach(p -> { p.getImages().size(); p.getVariants().size(); });
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public Page<ProductDto> getAllForAdmin(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::toDto);
    }

    @Transactional
    public ProductDto create(ProductDto dto) {
        Product product = Product.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .originalPrice(dto.getOriginalPrice())
                .stock(dto.getStock() != null ? dto.getStock() : 0)
                .brand(dto.getBrand() != null ? dto.getBrand() : "Leo Wear")
                .material(dto.getMaterial())
                .features(dto.getFeatures())
                .color(dto.getColor())
                .size(dto.getSize())
                .category(resolveCategory(dto.getCategoryId()))
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        applyImages(product, dto);
        applyVariants(product, dto);
        product.recalculateStockFromVariants();
        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto update(Long id, ProductDto dto) {
        Product product = productRepository.findDetailedById(id)
                .orElseGet(() -> productRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id)));

        // Ensure collections initialized inside the transaction
        product.getImages().size();
        product.getVariants().size();

        if (dto.getName() != null && !dto.getName().isBlank()) product.setName(dto.getName().trim());
        if (dto.getDescription() != null) product.setDescription(dto.getDescription());
        if (dto.getPrice() != null) product.setPrice(dto.getPrice());
        // Allow clearing original price when client sends null via explicit flag — keep previous if absent
        product.setOriginalPrice(dto.getOriginalPrice());
        if (dto.getStock() != null) product.setStock(dto.getStock());
        if (dto.getBrand() != null) product.setBrand(dto.getBrand());
        if (dto.getMaterial() != null) product.setMaterial(dto.getMaterial());
        if (dto.getFeatures() != null) product.setFeatures(dto.getFeatures());
        if (dto.getColor() != null) product.setColor(dto.getColor());
        if (dto.getSize() != null) product.setSize(dto.getSize());
        if (dto.getActive() != null) product.setActive(dto.getActive());
        if (dto.getCategoryId() != null) {
            product.setCategory(resolveCategory(dto.getCategoryId()));
        }

        // Always replace images/variants when provided (including empty list = clear)
        if (dto.getImageList() != null || dto.getImageUrls() != null || dto.getImages() != null) {
            applyImages(product, dto);
        }
        if (dto.getVariants() != null) {
            applyVariants(product, dto);
            product.recalculateStockFromVariants();
        }

        product = productRepository.saveAndFlush(product);
        return toDto(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.setActive(false);
        productRepository.save(product);
    }

    @Transactional
    public ProductDto updateStock(Long id, Integer stock) {
        if (stock < 0) throw new BadRequestException("Stock cannot be negative");
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.setStock(stock);
        return toDto(productRepository.save(product));
    }

    /**
     * Single-purpose flag flip. Used by staff (ADMIN/EMPLOYEE) to activate or
     * deactivate a product via {@code PATCH /api/admin/products/{id}/active}.
     * Separated from {@link #update(Long, ProductDto)} so the reactivation
     * path is easy to audit and lock down.
     */
    @Transactional
    public ProductDto setActive(Long id, Boolean active) {
        if (active == null) {
            throw new BadRequestException("active flag is required");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.setActive(active);
        return toDto(productRepository.saveAndFlush(product));
    }

    private void applyImages(Product product, ProductDto dto) {
        product.getImages().clear();

        List<ProductImageDto> list = dto.getImageList();
        if (list == null || list.isEmpty()) {
            list = dto.getImages();
        }
        if ((list == null || list.isEmpty()) && dto.getImageUrls() != null) {
            list = new ArrayList<>();
            int primary = dto.getPrimaryImageIndex() != null ? dto.getPrimaryImageIndex() : 0;
            for (int i = 0; i < dto.getImageUrls().size(); i++) {
                String url = dto.getImageUrls().get(i);
                if (url == null || url.isBlank()) continue;
                list.add(ProductImageDto.builder()
                        .url(url.trim())
                        .primary(i == primary)
                        .sortOrder(i)
                        .build());
            }
        }
        if (list == null || list.isEmpty()) return;

        boolean anyPrimary = list.stream().anyMatch(ProductImageDto::isPrimary);
        int order = 0;
        for (int i = 0; i < list.size(); i++) {
            ProductImageDto imgDto = list.get(i);
            if (imgDto.getUrl() == null || imgDto.getUrl().isBlank()) continue;
            boolean isPrimary = anyPrimary ? imgDto.isPrimary() : i == 0;
            product.getImages().add(ProductImage.builder()
                    .product(product)
                    .url(imgDto.getUrl().trim())
                    .primary(isPrimary)
                    .sortOrder(imgDto.getSortOrder() > 0 ? imgDto.getSortOrder() : order++)
                    .color(blankToNull(imgDto.getColor()))
                    .size(blankToNull(imgDto.getSize()))
                    .build());
        }
        product.syncPrimaryImageUrl();
    }

    /**
     * Merge variants by size+color to avoid unique-constraint violations
     * (Hibernate can INSERT before DELETE when using clear()+add).
     */
    private void applyVariants(Product product, ProductDto dto) {
        if (dto.getVariants() == null) {
            return;
        }

        Map<String, ProductVariant> existing = new LinkedHashMap<>();
        for (ProductVariant v : product.getVariants()) {
            existing.put(variantKey(v.getSize(), v.getColor()), v);
        }

        Set<String> keep = new HashSet<>();
        for (ProductVariantDto v : dto.getVariants()) {
            if (v.getSize() == null || v.getSize().isBlank() || v.getColor() == null || v.getColor().isBlank()) {
                continue;
            }
            String size = v.getSize().trim();
            String color = v.getColor().trim();
            String key = variantKey(size, color);
            if (!keep.add(key)) {
                continue; // skip duplicate in request
            }

            ProductVariant current = existing.get(key);
            if (current != null) {
                current.setStock(v.getStock() != null ? v.getStock() : 0);
                current.setPrice(v.getPrice());
                current.setSku(v.getSku());
                current.setActive(v.getActive() != null ? v.getActive() : true);
                current.setSize(size);
                current.setColor(color);
            } else {
                product.getVariants().add(ProductVariant.builder()
                        .product(product)
                        .size(size)
                        .color(color)
                        .stock(v.getStock() != null ? v.getStock() : 0)
                        .price(v.getPrice())
                        .sku(v.getSku())
                        .active(v.getActive() != null ? v.getActive() : true)
                        .build());
            }
        }

        // Remove variants no longer in the payload
        product.getVariants().removeIf(v -> !keep.contains(variantKey(v.getSize(), v.getColor())));
    }

    private String variantKey(String size, String color) {
        return (size == null ? "" : size.trim().toLowerCase()) + "|"
                + (color == null ? "" : color.trim().toLowerCase());
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public ProductDto toDto(Product p) {
        ProductDto dto = new ProductDto();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setPrice(p.getPrice());
        dto.setOriginalPrice(p.getOriginalPrice());
        dto.setStock(p.getStock());
        dto.setBrand(p.getBrand());
        dto.setMaterial(p.getMaterial());
        dto.setFeatures(p.getFeatures());
        dto.setColor(p.getColor());
        dto.setSize(p.getSize());
        dto.setImageUrl(p.getImageUrl());
        dto.setActive(p.getActive());
        if (p.getCategory() != null) {
            dto.setCategoryId(p.getCategory().getId());
            dto.setCategoryName(p.getCategory().getName());
            if (p.getCategory().getParent() != null) {
                dto.setParentCategoryId(p.getCategory().getParent().getId());
                dto.setParentCategoryName(p.getCategory().getParent().getName());
            }
            dto.setSizeGuide(p.getCategory().getSizeGuide());
        }

        List<ProductImageDto> imageDtos = p.getImages().stream()
                .sorted(Comparator
                        .comparing(ProductImage::isPrimary).reversed()
                        .thenComparingInt(ProductImage::getSortOrder))
                .map(img -> ProductImageDto.builder()
                        .id(img.getId())
                        .url(img.getUrl())
                        .primary(img.isPrimary())
                        .sortOrder(img.getSortOrder())
                        .color(img.getColor())
                        .size(img.getSize())
                        .build())
                .collect(Collectors.toList());
        if (imageDtos.isEmpty() && p.getImageUrl() != null) {
            imageDtos.add(ProductImageDto.builder().url(p.getImageUrl()).primary(true).sortOrder(0).build());
        }
        dto.setImages(imageDtos);
        dto.setImageUrls(imageDtos.stream().map(ProductImageDto::getUrl).collect(Collectors.toList()));

        List<ProductVariantDto> variantDtos = p.getVariants().stream()
                .filter(v -> Boolean.TRUE.equals(v.getActive()))
                .map(v -> ProductVariantDto.builder()
                        .id(v.getId())
                        .size(v.getSize())
                        .color(v.getColor())
                        .stock(v.getStock())
                        .price(v.getPrice())
                        .sku(v.getSku())
                        .active(v.getActive())
                        .build())
                .collect(Collectors.toList());
        dto.setVariants(variantDtos);

        dto.setAvailableSizes(variantDtos.stream().map(ProductVariantDto::getSize).distinct().sorted().collect(Collectors.toList()));
        dto.setAvailableColors(variantDtos.stream().map(ProductVariantDto::getColor).distinct().sorted().collect(Collectors.toList()));

        return dto;
    }
}
