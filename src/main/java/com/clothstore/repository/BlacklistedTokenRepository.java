package com.clothstore.repository;

import com.clothstore.entity.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    boolean existsByJti(String jti);

    @Modifying
    @Query("DELETE FROM BlacklistedToken bt WHERE bt.expiryDate < CURRENT_TIMESTAMP")
    int deleteExpired();
}
