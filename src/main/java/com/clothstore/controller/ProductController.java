package com.clothstore.controller;

import com.clothstore.dto.ApiResponse;
import com.clothstore.dto.ProductDto;
import com.clothstore.exception.ResourceNotFoundException;
import com.clothstore.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductDto>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);

        Page<ProductDto> products;
        boolean hasSearch = search != null && !search.isBlank();
        if (hasSearch && categoryId != null) {
            products = productService.searchInCategory(search, categoryId, pageable);
        } else if (hasSearch) {
            products = productService.search(search, pageable);
        } else if (categoryId != null) {
            products = productService.getByCategory(categoryId, pageable);
        } else {
            products = productService.getAllActive(pageable);
        }

        return ResponseEntity.ok(ApiResponse.ok(products));
    }

    @GetMapping("/bestsellers")
    public ResponseEntity<ApiResponse<List<ProductDto>>> bestsellers(
            @RequestParam(defaultValue = "8") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getBestsellers(limit)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> getById(@PathVariable Long id, Authentication auth) {
        ProductDto dto = productService.getById(id);
        // Hide inactive products from customers — they must only see active=true items.
        // Staff (ADMIN/EMPLOYEE) still see inactive products so they can manage them.
        if (!Boolean.TRUE.equals(dto.getActive()) && !isStaff(auth)) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    private static boolean isStaff(Authentication auth) {
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String name = a.getAuthority();
            if (SimpleGrantedAuthority.class.isAssignableFrom(a.getClass())
                    && ("ROLE_ADMIN".equals(name) || "ROLE_EMPLOYEE".equals(name))) {
                return true;
            }
        }
        return false;
    }
}
