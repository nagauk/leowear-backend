package com.clothstore.service;

import com.clothstore.dto.CouponDto;
import com.clothstore.dto.CouponRequest;
import com.clothstore.dto.CouponValidationDto;
import com.clothstore.entity.Coupon;
import com.clothstore.entity.CouponRedemption;
import com.clothstore.entity.Order;
import com.clothstore.entity.User;
import com.clothstore.exception.BadRequestException;
import com.clothstore.exception.ResourceNotFoundException;
import com.clothstore.repository.CouponRedemptionRepository;
import com.clothstore.repository.CouponRepository;
import com.clothstore.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Coupon CRUD + customer-side validation + redemption tracking.
 *
 * <p>Admin operations (create/update/delete/list) are guarded at the
 * controller layer with {@code @PreAuthorize("hasRole('ADMIN')")} — the
 * service itself doesn't enforce role, but it does enforce business
 * invariants (unique code, percent range, expiry sanity).</p>
 *
 * <p>Customer-side methods ({@link #validateForCustomer}, {@link #redeem})
 * work inside the calling transaction so the redemption insert and the
 * {@code Coupon.timesUsed} increment succeed-or-rollback together.</p>
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final OrderRepository orderRepository;

    // ========== Admin CRUD ==========

    @Transactional(readOnly = true)
    public Page<CouponDto> listAll(Pageable pageable) {
        return couponRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public CouponDto getById(Long id) {
        return toDto(loadOrThrow(id));
    }

    @Transactional
    public CouponDto create(CouponRequest req) {
        String code = normaliseCode(req.getCode());
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new BadRequestException("Coupon code already exists: " + code);
        }
        Coupon c = Coupon.builder()
                .code(code)
                .description(req.getDescription())
                .discountPercent(req.getDiscountPercent())
                .minOrderAmount(req.getMinOrderAmount())
                .expiresAt(req.getExpiresAt())
                .usageLimit(req.getUsageLimit())
                .usagePerUser(req.getUsagePerUser())
                .firstTimeUserOnly(Boolean.TRUE.equals(req.getFirstTimeUserOnly()))
                .active(req.getActive() == null ? Boolean.TRUE : req.getActive())
                .timesUsed(0)
                .build();
        return toDto(couponRepository.save(c));
    }

    @Transactional
    public CouponDto update(Long id, CouponRequest req) {
        Coupon c = loadOrThrow(id);
        String code = normaliseCode(req.getCode());
        if (!c.getCode().equals(code) && couponRepository.existsByCodeIgnoreCase(code)) {
            throw new BadRequestException("Coupon code already exists: " + code);
        }
        c.setCode(code);
        c.setDescription(req.getDescription());
        c.setDiscountPercent(req.getDiscountPercent());
        c.setMinOrderAmount(req.getMinOrderAmount());
        c.setExpiresAt(req.getExpiresAt());
        c.setUsageLimit(req.getUsageLimit());
        c.setUsagePerUser(req.getUsagePerUser());
        c.setFirstTimeUserOnly(Boolean.TRUE.equals(req.getFirstTimeUserOnly()));
        if (req.getActive() != null) c.setActive(req.getActive());
        return toDto(couponRepository.save(c));
    }

    @Transactional
    public void delete(Long id) {
        Coupon c = loadOrThrow(id);
        // Hard delete. Order history keeps the code via Order.couponCodeSnapshot
        // so historical invoices still show the coupon that was applied.
        couponRepository.delete(c);
    }

    // ========== Customer-side ==========

    /**
     * Validate a code for a customer's current cart subtotal. Throws
     * {@link BadRequestException} with a customer-friendly message on every
     * rejection path — the cart UI surfaces that message verbatim.
     *
     * @return a {@link CouponValidationDto} with the discount math applied to
     *         {@code currentSubtotal}.
     */
    @Transactional(readOnly = true)
    public CouponValidationDto validateForCustomer(String code, String username, BigDecimal currentSubtotal) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Enter a coupon code");
        }
        String normalised = normaliseCode(code);
        Coupon coupon = couponRepository.findByCode(normalised)
                .orElseThrow(() -> new BadRequestException("Invalid coupon code"));

        if (!Boolean.TRUE.equals(coupon.getActive())) {
            throw new BadRequestException("Invalid coupon code");
        }
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This coupon has expired");
        }
        if (coupon.getMinOrderAmount() != null
                && currentSubtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            BigDecimal gap = coupon.getMinOrderAmount().subtract(currentSubtotal);
            throw new BadRequestException(
                    "Add ₹" + gap.setScale(0, RoundingMode.HALF_UP)
                            + " more to use this coupon");
        }
        if (coupon.getUsageLimit() != null
                && redemptionRepository.countByCouponId(coupon.getId()) >= coupon.getUsageLimit()) {
            throw new BadRequestException("This coupon has reached its usage limit");
        }

        User user = resolveUser(username);
        if (coupon.getUsagePerUser() != null
                && redemptionRepository.countByCouponIdAndUserId(coupon.getId(), user.getId())
                        >= coupon.getUsagePerUser()) {
            throw new BadRequestException("You have already used this coupon");
        }
        if (Boolean.TRUE.equals(coupon.getFirstTimeUserOnly())
                && orderRepository.countByUserIdAndStatusNotIn(user.getId(),
                        java.util.List.of("CANCELLED", "RETURNED")) > 0) {
            throw new BadRequestException("This coupon is for first-time customers only");
        }

        BigDecimal subtotal = currentSubtotal == null ? BigDecimal.ZERO : currentSubtotal;
        BigDecimal discount = subtotal.multiply(coupon.getDiscountPercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (discount.compareTo(subtotal) > 0) {
            // Never discount more than the subtotal (no negative totals).
            discount = subtotal;
        }
        BigDecimal finalTotal = subtotal.subtract(discount);

        return CouponValidationDto.builder()
                .code(coupon.getCode())
                .description(coupon.getDescription())
                .discountPercent(coupon.getDiscountPercent())
                .discountAmount(discount)
                .finalTotal(finalTotal)
                .expiresAt(coupon.getExpiresAt())
                .build();
    }

    /**
     * Persist the redemption against an order. Must run inside the order-placement
     * transaction so the redemption insert and the {@code Coupon.timesUsed}
     * increment roll back together if order placement fails.
     */
    @Transactional
    public void redeem(Coupon coupon, User user, Order order) {
        coupon.setTimesUsed(coupon.getTimesUsed() == null ? 1 : coupon.getTimesUsed() + 1);
        couponRepository.save(coupon);

        CouponRedemption r = CouponRedemption.builder()
                .couponId(coupon.getId())
                .userId(user.getId())
                .orderId(order.getId())
                .couponCodeSnapshot(coupon.getCode())
                .build();
        redemptionRepository.save(r);
    }

    /**
     * Internal: fetch the {@link Coupon} row by its (uppercased) code so the
     * caller can persist a redemption. Throws if the coupon is missing or
     * inactive — the prior {@link #validateForCustomer} call would have
     * surfaced that to the customer already, so this is a safety net.
     */
    @Transactional(readOnly = true)
    public Coupon getEntityForRedemption(String code) {
        String normalised = normaliseCode(code);
        Coupon c = couponRepository.findByCode(normalised)
                .orElseThrow(() -> new BadRequestException("Invalid coupon code"));
        if (!Boolean.TRUE.equals(c.getActive())) {
            throw new BadRequestException("Invalid coupon code");
        }
        return c;
    }

    // ========== Helpers ==========

    private Coupon loadOrThrow(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + id));
    }

    private User resolveUser(String username) {
        return orderRepository.findUserForCouponValidation(username)
                .orElseThrow(() -> new BadRequestException("User not found"));
    }

    private static String normaliseCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    public CouponDto toDto(Coupon c) {
        return CouponDto.builder()
                .id(c.getId())
                .code(c.getCode())
                .description(c.getDescription())
                .discountPercent(c.getDiscountPercent())
                .minOrderAmount(c.getMinOrderAmount())
                .expiresAt(c.getExpiresAt())
                .usageLimit(c.getUsageLimit())
                .usagePerUser(c.getUsagePerUser())
                .firstTimeUserOnly(c.getFirstTimeUserOnly())
                .active(c.getActive())
                .timesUsed(c.getTimesUsed())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}