package com.clothstore.service;

import com.clothstore.entity.RefreshToken;
import com.clothstore.entity.User;
import com.clothstore.exception.BadRequestException;
import com.clothstore.repository.RefreshTokenRepository;
import com.clothstore.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles refresh-token lifecycle with <b>rotation</b>:
 * <ul>
 *   <li>On every successful refresh the old token is revoked and a new one is issued.</li>
 *   <li>If a previously rotated (revoked) token is presented again, the entire
 *       token family for that user is revoked — this detects token theft.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(jwtUtil.generateRefreshTokenValue())
                .expiryDate(Instant.now().plusMillis(jwtUtil.getRefreshTokenExpirationMs()))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Validate and <b>rotate</b> the refresh token.
     * Returns the newly created refresh token; the old one is marked revoked.
     *
     * @throws BadRequestException if the token is invalid, expired, or was already used (theft detection)
     */
    @Transactional
    public RefreshToken rotateRefreshToken(String tokenValue) {
        RefreshToken existing = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        // Reuse of an already-revoked/rotated token → possible theft → revoke all for user
        if (existing.isRevoked()) {
            log.warn("Refresh token reuse detected for user id={}. Revoking all tokens.", existing.getUser().getId());
            refreshTokenRepository.revokeAllByUser(existing.getUser());
            throw new BadRequestException("Refresh token reuse detected. All sessions have been revoked. Please login again.");
        }

        if (existing.isExpired()) {
            existing.setRevoked(true);
            refreshTokenRepository.save(existing);
            throw new BadRequestException("Refresh token has expired. Please login again.");
        }

        // Rotate: create new token, mark old as revoked and link them
        RefreshToken newToken = createRefreshToken(existing.getUser());

        existing.setRevoked(true);
        existing.setReplacedByToken(newToken.getToken());
        refreshTokenRepository.save(existing);

        return newToken;
    }

    @Transactional
    public void revokeToken(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllByUser(user);
    }
}
