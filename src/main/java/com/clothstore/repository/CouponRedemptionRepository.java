package com.clothstore.repository;

import com.clothstore.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    long countByCouponId(Long couponId);

    long countByCouponIdAndUserId(Long couponId, Long userId);

    boolean existsByOrderId(Long orderId);
}