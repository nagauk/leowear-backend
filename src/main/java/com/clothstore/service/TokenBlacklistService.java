package com.clothstore.service;

import com.clothstore.entity.BlacklistedToken;
import com.clothstore.repository.BlacklistedTokenRepository;
import com.clothstore.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;

/**
 * Blacklists JWT access tokens by their {@code jti} claim so they are rejected
 * immediately on logout (or forced revocation), rather than waiting for natural expiry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final JwtUtil jwtUtil;

    /**
     * Parse the access token, extract jti + expiry, and add to blacklist.
     * Silently ignores invalid/already-expired tokens.
     */
    @Transactional
    public void blacklistAccessToken(String accessToken, String reason) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            String jti = jwtUtil.extractJti(accessToken);
            if (jti == null || jti.isBlank()) {
                log.warn("Access token has no jti claim — cannot blacklist");
                return;
            }
            if (blacklistedTokenRepository.existsByJti(jti)) {
                return; // already blacklisted
            }

            Date exp = jwtUtil.extractExpiration(accessToken);
            Instant expiry = exp != null ? exp.toInstant() : Instant.now().plusSeconds(900);

            // No point blacklisting an already-expired token
            if (expiry.isBefore(Instant.now())) {
                return;
            }

            String username = null;
            try {
                username = jwtUtil.extractUsername(accessToken);
            } catch (Exception ignored) { }

            blacklistedTokenRepository.save(BlacklistedToken.builder()
                    .jti(jti)
                    .username(username)
                    .expiryDate(expiry)
                    .reason(reason != null ? reason : "logout")
                    .build());

            log.debug("Blacklisted access token jti={} user={}", jti, username);
        } catch (Exception e) {
            log.warn("Failed to blacklist access token: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return blacklistedTokenRepository.existsByJti(jti);
    }

    /** Convenience: extract jti from raw JWT and check blacklist */
    public boolean isAccessTokenBlacklisted(String accessToken) {
        try {
            String jti = jwtUtil.extractJti(accessToken);
            return isBlacklisted(jti);
        } catch (Exception e) {
            return false;
        }
    }

    /** Purge expired blacklist entries every hour */
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void cleanupExpired() {
        int deleted = blacklistedTokenRepository.deleteExpired();
        if (deleted > 0) {
            log.info("Cleaned up {} expired blacklisted tokens", deleted);
        }
    }
}
