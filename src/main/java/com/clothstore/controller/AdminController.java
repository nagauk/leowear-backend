package com.clothstore.controller;

import com.clothstore.dto.ApiResponse;
import com.clothstore.dto.DashboardStats;
import com.clothstore.dto.ProductDto;
import com.clothstore.dto.CategoryDto;
import com.clothstore.service.CategoryService;
import com.clothstore.service.DashboardService;
import com.clothstore.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final DashboardService dashboardService;

    // ========== Dashboard (ADMIN only) ==========
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<DashboardStats>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getStats()));
    }

    // ========== Products (ADMIN + EMPLOYEE) ==========
    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Page<ProductDto>>> allProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getAllForAdmin(PageRequest.of(page, size, Sort.by("id").descending()))));
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(@Valid @RequestBody ProductDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Product created", productService.create(dto)));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<ProductDto>> updateProduct(
            @PathVariable Long id, @Valid @RequestBody ProductDto dto) {
        return ResponseEntity.ok(ApiResponse.ok("Product updated", productService.update(id, dto)));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Product deactivated", null));
    }

    @PatchMapping("/products/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<ProductDto>> updateStock(
            @PathVariable Long id, @RequestParam Integer stock) {
        return ResponseEntity.ok(ApiResponse.ok("Stock updated", productService.updateStock(id, stock)));
    }

    /**
     * Single-purpose reactivate / deactivate endpoint. The only sanctioned
     * way for staff to flip a product's {@code active} flag — separated from
     * the full {@code PUT /products/{id}} edit so the reactivation path is
     * easy to audit. Customers cannot reach it (ADMIN/EMPLOYEE only).
     */
    @PatchMapping("/products/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<ProductDto>> setActive(
            @PathVariable Long id, @RequestParam Boolean active) {
        return ResponseEntity.ok(ApiResponse.ok(
                Boolean.TRUE.equals(active) ? "Product reactivated" : "Product deactivated",
                productService.setActive(id, active)));
    }

    // ========== Categories ==========
    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(@RequestBody CategoryDto category) {
        return ResponseEntity.ok(ApiResponse.ok("Category created", categoryService.create(category)));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ApiResponse<CategoryDto>> updateCategory(
            @PathVariable Long id, @RequestBody CategoryDto category) {
        return ResponseEntity.ok(ApiResponse.ok("Category updated", categoryService.update(id, category)));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Category deleted", null));
    }
}

