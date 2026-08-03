package com.clothstore.security;

import com.clothstore.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Stores every /api/* request in audit_logs for ANONYMOUS, CUSTOMER and ADMIN.
 * Not exposed in any UI.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@RequiredArgsConstructor
public class AuditFilter extends OncePerRequestFilter {

    private final AuditService auditService;

    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/h2-console",
            "/actuator",
            "/favicon",
            "/error"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) return true;
        if (!path.startsWith("/api/")) return true;
        for (String skip : SKIP_PREFIXES) {
            if (path.startsWith(skip)) return true;
        }
        return path.startsWith("/api/audit");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrapped = request instanceof ContentCachingRequestWrapper
                ? (ContentCachingRequestWrapper) request
                : new ContentCachingRequestWrapper(request, 4096);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            try {
                int status = response.getStatus();
                String method = wrapped.getMethod();
                String path = wrapped.getRequestURI();
                String query = wrapped.getQueryString();
                String resource = query != null ? path + "?" + query : path;

                String action = resolveAction(method, path);

                String details = null;
                if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                    byte[] buf = wrapped.getContentAsByteArray();
                    if (buf.length > 0) {
                        details = new String(buf, StandardCharsets.UTF_8);
                        if (details.length() > 1500) {
                            details = details.substring(0, 1500) + "...";
                        }
                    }
                }
                details = (details != null ? details + " | " : "")
                        + "durationMs=" + (System.currentTimeMillis() - start);

                String username = "ANONYMOUS";
                String role = "ANONYMOUS";
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()
                        && auth.getPrincipal() != null
                        && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
                    username = auth.getName();
                    role = auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .filter(a -> a.startsWith("ROLE_"))
                            .map(a -> a.substring(5))
                            .findFirst()
                            .orElse("CUSTOMER");
                }

                auditService.log(
                        username,
                        role,
                        action,
                        method,
                        resource,
                        details,
                        clientIp(wrapped),
                        wrapped.getHeader("User-Agent"),
                        status,
                        status >= 200 && status < 400
                );
            } catch (Exception ignored) {
                // Never break the request because of audit
            }
        }
    }

    private String resolveAction(String method, String path) {
        if (path.contains("/auth/login") && "POST".equals(method)) return "LOGIN";
        if (path.contains("/auth/register") && "POST".equals(method)) return "REGISTER";
        if (path.contains("/auth/logout")) return "LOGOUT";
        if (path.contains("/auth/refresh")) return "TOKEN_REFRESH";
        if (path.contains("/orders") && "POST".equals(method) && !path.contains("/status")) return "ORDER_PLACE";
        if (path.contains("/orders") && path.contains("/status")) return "ORDER_STATUS_UPDATE";
        if (path.contains("/complaints") && "POST".equals(method)) return "COMPLAINT_CREATE";
        if (path.contains("/feedback") && "POST".equals(method)) return "FEEDBACK_SUBMIT";
        if (path.contains("/addresses") && "POST".equals(method)) return "ADDRESS_CREATE";
        if (path.contains("/addresses") && "PUT".equals(method)) return "ADDRESS_UPDATE";
        if (path.contains("/addresses") && "DELETE".equals(method)) return "ADDRESS_DELETE";
        if (path.contains("/admin/products") && "POST".equals(method)) return "PRODUCT_CREATE";
        if (path.contains("/admin/products") && "PUT".equals(method)) return "PRODUCT_UPDATE";
        if (path.contains("/admin/products") && "DELETE".equals(method)) return "PRODUCT_DELETE";
        if (path.contains("/settings") && "PUT".equals(method)) return "SETTINGS_UPDATE";
        if (path.contains("/returns") && "POST".equals(method)) return "RETURN_CREATE";
        if (path.contains("/pincode")) return "PINCODE_VALIDATE";
        return "HTTP_" + method;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }
}
