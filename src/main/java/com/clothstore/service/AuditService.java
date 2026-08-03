package com.clothstore.service;

import com.clothstore.entity.AuditLog;
import com.clothstore.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Persist audit entry in a separate transaction so audit failures never roll back business work.
     * Caller must pass username/role (SecurityContext is read in the request thread by AuditFilter).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String username, String role,
                    String action, String httpMethod, String resource, String details,
                    String ip, String userAgent, Integer statusCode, Boolean success) {
        try {
            String safeDetails = sanitize(details);

            AuditLog entry = AuditLog.builder()
                    .username(truncate(username != null ? username : "ANONYMOUS", 80))
                    .role(truncate(role != null ? role : "ANONYMOUS", 20))
                    .action(truncate(action, 60))
                    .httpMethod(truncate(httpMethod, 10))
                    .resource(truncate(resource, 500))
                    .details(truncate(safeDetails, 2000))
                    .ipAddress(truncate(ip, 64))
                    .userAgent(truncate(userAgent, 400))
                    .statusCode(statusCode)
                    .success(success)
                    .createdAt(LocalDateTime.now())
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    private String sanitize(String details) {
        if (details == null) return null;
        return details
                .replaceAll("(?i)\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"")
                .replaceAll("(?i)\"accessToken\"\\s*:\\s*\"[^\"]*\"", "\"accessToken\":\"***\"")
                .replaceAll("(?i)\"refreshToken\"\\s*:\\s*\"[^\"]*\"", "\"refreshToken\":\"***\"")
                .replaceAll("(?i)\"token\"\\s*:\\s*\"[^\"]*\"", "\"token\":\"***\"");
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
