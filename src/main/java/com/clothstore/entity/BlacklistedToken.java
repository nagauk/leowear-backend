package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Stores JWT IDs (jti) of revoked access tokens until they naturally expire.
 * After expiryDate the entry is safe to delete — the token is invalid anyway.
 */
@Entity
@Table(name = "blacklisted_tokens", indexes = {
        @Index(name = "idx_blacklist_jti", columnList = "jti", unique = true),
        @Index(name = "idx_blacklist_expiry", columnList = "expiry_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** JWT ID claim — unique per access token */
    @Column(nullable = false, unique = true, length = 64)
    private String jti;

    /** Username for audit / bulk revocation lookup */
    @Column(length = 50)
    private String username;

    /** When the original access token expires — blacklist entry is useless after this */
    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Column(name = "blacklisted_at", nullable = false)
    private Instant blacklistedAt;

    @Column(length = 100)
    private String reason;

    @PrePersist
    protected void onCreate() {
        if (blacklistedAt == null) {
            blacklistedAt = Instant.now();
        }
    }
}
