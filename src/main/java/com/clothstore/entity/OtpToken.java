package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_tokens", indexes = {
        @Index(name = "idx_otp_identifier_purpose", columnList = "identifier, purpose")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** email or phone (normalized) */
    @Column(nullable = false, length = 120)
    private String identifier;

    @Column(nullable = false, length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isValid(String input) {
        return !Boolean.TRUE.equals(used)
                && expiresAt != null
                && expiresAt.isAfter(LocalDateTime.now())
                && code != null
                && code.equals(input);
    }
}
