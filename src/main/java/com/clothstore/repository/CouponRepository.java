package com.clothstore.repository;

import com.clothstore.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * Case-insensitive lookup. Codes are stored uppercased but admins sometimes
     * paste them mixed-case — we normalise on read so callers don't have to.
     */
    @Query("SELECT c FROM Coupon c WHERE UPPER(c.code) = UPPER(:code)")
    Optional<Coupon> findByCode(@Param("code") String code);

    boolean existsByCodeIgnoreCase(String code);

    Page<Coupon> findAllByOrderByCreatedAtDesc(Pageable pageable);
}