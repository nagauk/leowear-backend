package com.clothstore.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_created", columnList = "created_at"),
        @Index(name = "idx_audit_username", columnList = "username"),
        @Index(name = "idx_audit_action", columnList = "action")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ANONYMOUS when not authenticated */
    @Column(length = 80)
    private String username;

    /** CUSTOMER / ADMIN / ANONYMOUS */
    @Column(length = 20)
    private String role;

    /** High-level action e.g. HTTP_GET, HTTP_POST, LOGIN, ORDER_PLACED */
    @Column(nullable = false, length = 60)
    private String action;

    /** HTTP method if applicable */
    @Column(length = 10)
    private String httpMethod;

    /** Request path / resource */
    @Column(length = 500)
    private String resource;

    /** Optional details (no passwords) */
    @Column(length = 2000)
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    /** HTTP status or business outcome code */
    private Integer statusCode;

    /** true if request succeeded (2xx) */
    private Boolean success;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
