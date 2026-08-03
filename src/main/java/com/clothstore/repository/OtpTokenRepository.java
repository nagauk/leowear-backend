package com.clothstore.repository;

import com.clothstore.entity.OtpPurpose;
import com.clothstore.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    Optional<OtpToken> findTopByIdentifierAndPurposeAndUsedFalseOrderByCreatedAtDesc(
            String identifier, OtpPurpose purpose);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE OtpToken o SET o.used = true WHERE o.identifier = :id AND o.purpose = :purpose AND o.used = false")
    void invalidateAll(@Param("id") String identifier, @Param("purpose") OtpPurpose purpose);
}
