package com.clothstore.controller;

import com.clothstore.dto.ApiResponse;
import com.clothstore.dto.CouponDto;
import com.clothstore.dto.CouponRequest;
import com.clothstore.dto.CouponValidateRequest;
import com.clothstore.dto.CouponValidationDto;
import com.clothstore.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Coupon resource.
 *
 * <ul>
 *   <li>Admin-only: list, create, update, delete, get-by-id (paths under
 *       {@code /api/coupons} that are not {@code /validate}). Employees
 *       and customers cannot list or view coupons — the {@code @PreAuthorize}
 *       and the {@code SecurityConfig} matcher both block them.</li>
 *   <li>Customer-only: {@code POST /validate} — applies a typed code to a
 *       current subtotal and returns the discount preview. Authentication +
 *       CUSTOMER role enforced by {@code @PreAuthorize}; admin/employee
 *       tokens can't redeem coupons.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    // ========== Admin CRUD ==========

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<CouponDto>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                couponService.listAll(PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CouponDto>> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(couponService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CouponDto>> create(@Valid @RequestBody CouponRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Coupon created", couponService.create(req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CouponDto>> update(
            @PathVariable Long id,
            @Valid @RequestBody CouponRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Coupon updated", couponService.update(id, req)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        couponService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Coupon deleted", null));
    }

    // ========== Customer-side validation ==========

    /**
     * Apply a typed code to a customer's cart subtotal. Returns the discount
     * math (preview only — actual redemption happens during order placement,
     * inside the same transaction).
     */
    @PostMapping("/validate")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<CouponValidationDto>> validate(
            @Valid @RequestBody CouponValidateRequest req,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                couponService.validateForCustomer(req.getCode(), auth.getName(), req.getSubtotal())));
    }
}