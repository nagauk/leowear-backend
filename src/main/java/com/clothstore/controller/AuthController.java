package com.clothstore.controller;

import com.clothstore.dto.*;
import com.clothstore.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Registration successful", authService.register(request)));
    }

    @GetMapping("/check-username")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkUsername(@RequestParam String value) {
        boolean available = authService.isUsernameAvailable(value);
        return ResponseEntity.ok(ApiResponse.ok(
                available ? "Username is available" : "Username is already registered",
                Map.of("available", available, "field", "username")));
    }

    @GetMapping("/check-email")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkEmail(@RequestParam String value) {
        boolean available = authService.isEmailAvailable(value);
        return ResponseEntity.ok(ApiResponse.ok(
                available ? "Email is available" : "Email is already registered",
                Map.of("available", available, "field", "email")));
    }

    @GetMapping("/check-phone")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkPhone(@RequestParam String value) {
        boolean available = authService.isPhoneAvailable(value);
        return ResponseEntity.ok(ApiResponse.ok(
                available ? "Mobile number is available" : "Mobile number is already registered",
                Map.of("available", available, "field", "phone")));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful", authService.login(request)));
    }

    /**
     * Exchange a valid refresh token for a new access token + rotated refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", authService.refresh(request.getRefreshToken())));
    }

    /**
     * Logout current session:
     * - Blacklists the access token (from Authorization header or body)
     * - Revokes the refresh token
     * Body: { "refreshToken": "...", "accessToken": "..." } (both optional if header present)
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {

        String accessToken = extractBearer(request);
        if (accessToken == null && body != null) {
            accessToken = body.get("accessToken");
        }
        String refreshToken = body != null ? body.get("refreshToken") : null;

        authService.logout(accessToken, refreshToken);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully", null));
    }

    /**
     * Logout all sessions — revoke all refresh tokens + blacklist current access token.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            Authentication authentication,
            HttpServletRequest request) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }
        String accessToken = extractBearer(request);
        authService.logoutAll(authentication.getName(), accessToken);
        return ResponseEntity.ok(ApiResponse.ok("All sessions revoked", null));
    }

    @PostMapping("/otp/send")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendOtp(@RequestBody Map<String, String> body) {
        String identifier = body.get("identifier");
        String purpose = body.get("purpose");
        authService.sendOtp(identifier, purpose);
        return ResponseEntity.ok(ApiResponse.ok(
                "OTP sent. Check server logs in development.",
                Map.of("status", "SENT", "message", "OTP valid for 10 minutes")));
    }

    @PostMapping("/otp/verify-email")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@RequestBody Map<String, String> body,
                                                                  Authentication authentication) {
        String username = authentication != null ? authentication.getName() : body.get("username");
        return ResponseEntity.ok(ApiResponse.ok("Email verified",
                authService.verifyEmail(body.get("email"), body.get("code"), username)));
    }

    @PostMapping("/otp/verify-phone")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyPhone(@RequestBody Map<String, String> body,
                                                                  Authentication authentication) {
        String username = authentication != null ? authentication.getName() : body.get("username");
        return ResponseEntity.ok(ApiResponse.ok("Phone verified",
                authService.verifyPhone(body.get("phone"), body.get("code"), username)));
    }

    @PostMapping("/login-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> loginOtp(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok("Login successful",
                authService.loginWithOtp(body.get("identifier"), body.get("code"))));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(@RequestBody Map<String, String> body) {
        authService.forgotPassword(body.get("identifier"));
        return ResponseEntity.ok(ApiResponse.ok(
                "If an account exists, an OTP has been sent.",
                Map.of("status", "OK")));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody Map<String, String> body) {
        authService.resetPassword(body.get("identifier"), body.get("code"), body.get("newPassword"));
        return ResponseEntity.ok(ApiResponse.ok("Password reset successful", null));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @RequestBody Map<String, String> body) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }
        authService.changePassword(authentication.getName(), body.get("currentPassword"), body.get("newPassword"));
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", null));
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
