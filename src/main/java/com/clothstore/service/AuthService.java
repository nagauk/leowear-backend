package com.clothstore.service;

import com.clothstore.dto.*;
import com.clothstore.entity.*;
import com.clothstore.exception.BadRequestException;
import com.clothstore.repository.UserRepository;
import com.clothstore.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final OtpService otpService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String phone = normalizePhone(request.getPhone());

        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Username is already registered. Please choose another.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email is already registered. Try logging in or use a different email.");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new BadRequestException("Mobile number is already registered with another account.");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(phone)
                .address(request.getAddress())
                .role(Role.CUSTOMER)
                .emailVerified(false)
                .phoneVerified(false)
                .build();

        user = userRepository.save(user);

        // Send verification OTPs (logged in server console for dev)
        try {
            otpService.sendOtp(user.getEmail(), OtpPurpose.EMAIL_VERIFY);
            if (user.getPhone() != null) {
                otpService.sendOtp(user.getPhone(), OtpPurpose.PHONE_VERIFY);
            }
        } catch (Exception ignored) { }

        return issueTokens(user);
    }

    /** Check availability for live validation on signup form */
    public boolean isUsernameAvailable(String username) {
        if (username == null || username.isBlank()) return false;
        return !userRepository.existsByUsername(username.trim());
    }


    /** Admin-only: create EMPLOYEE user with username/password */
    @Transactional
    public AuthResponse createEmployee(RegisterRequest request) {
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (username.isBlank() || email.isBlank() || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException("Username, email and password are required for employee");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email is already registered");
        }
        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(Role.EMPLOYEE)
                .emailVerified(true)
                .phoneVerified(false)
                .build();
        userRepository.save(user);
        return AuthResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .emailVerified(true)
                .phoneVerified(false)
                .build();
    }

    public boolean isEmailAvailable(String email) {
        if (email == null || email.isBlank()) return false;
        return !userRepository.existsByEmail(email.trim().toLowerCase());
    }

    public boolean isPhoneAvailable(String phone) {
        String p = normalizePhone(phone);
        if (p == null) return true; // empty phone is allowed
        return !userRepository.existsByPhone(p);
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+91") && digits.length() == 13) {
            return digits.substring(3);
        }
        if (digits.startsWith("91") && digits.length() == 12) {
            return digits.substring(2);
        }
        return digits.isBlank() ? null : digits;
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadRequestException("User not found"));

        return issueTokens(user);
    }

    /**
     * Exchange a valid refresh token for a new access token + rotated refresh token.
     */
    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(refreshTokenValue);
        User user = newRefreshToken.getUser();
        AuthResponse res = issueTokens(user);
        // issueTokens creates a NEW refresh token; we already rotated — replace with rotated value
        res.setRefreshToken(newRefreshToken.getToken());
        return res;
    }

    /**
     * Logout current session:
     * <ul>
     *   <li>Blacklist the access token (immediate invalidation via jti)</li>
     *   <li>Revoke the refresh token</li>
     * </ul>
     */
    @Transactional
    public void logout(String accessToken, String refreshTokenValue) {
        if (accessToken != null && !accessToken.isBlank()) {
            tokenBlacklistService.blacklistAccessToken(accessToken, "logout");
        }
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenService.revokeToken(refreshTokenValue);
        }
    }

    /**
     * Revoke all refresh tokens for the user and blacklist the current access token.
     */
    @Transactional
    public void logoutAll(String username, String accessToken) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("User not found"));
        refreshTokenService.revokeAllUserTokens(user);
        if (accessToken != null && !accessToken.isBlank()) {
            tokenBlacklistService.blacklistAccessToken(accessToken, "logout-all");
        }
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getUsername(), user.getRole().name());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .type("Bearer")
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .role(user.getRole())
                .expiresIn(jwtUtil.getAccessTokenExpirationMs() / 1000)
                .emailVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .phoneVerified(Boolean.TRUE.equals(user.getPhoneVerified()))
                .build();
    }

    // ── OTP / password flows ──────────────────────────────────────────

    @Transactional
    public void sendOtp(String identifier, String purposeStr) {
        OtpPurpose purpose = parsePurpose(purposeStr);
        // Mobile OTP / SMS temporarily disabled — email only
        if (purpose == OtpPurpose.PHONE_VERIFY) {
            throw new BadRequestException("Mobile OTP is temporarily disabled. Please verify email instead.");
        }
        if (identifier != null && !identifier.contains("@") && purpose != OtpPurpose.PHONE_VERIFY) {
            // LOGIN / RESET via phone blocked while SMS is off
            if (purpose == OtpPurpose.LOGIN || purpose == OtpPurpose.RESET_PASSWORD) {
                throw new BadRequestException("Mobile OTP is temporarily disabled. Use your registered email.");
            }
        }
        String id = identifier != null ? identifier.trim() : "";
        if (purpose == OtpPurpose.LOGIN || purpose == OtpPurpose.RESET_PASSWORD) {
            User user = findByEmailOrPhone(id);
            if (user == null) {
                throw new BadRequestException("No account found for this email or mobile");
            }
            id = purpose == OtpPurpose.LOGIN && id.contains("@") ? user.getEmail()
                    : (id.contains("@") ? user.getEmail() : user.getPhone());
        }
        if (purpose == OtpPurpose.EMAIL_VERIFY) {
            id = id.toLowerCase();
        }
        otpService.sendOtp(id, purpose);
    }

    @Transactional
    public AuthResponse verifyEmail(String email, String code, String username) {
        User user = username != null
                ? userRepository.findByUsername(username).orElseThrow(() -> new BadRequestException("User not found"))
                : userRepository.findByEmail(email.trim().toLowerCase())
                    .orElseThrow(() -> new BadRequestException("User not found"));
        otpService.verifyOtp(user.getEmail(), OtpPurpose.EMAIL_VERIFY, code);
        user.setEmailVerified(true);
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse verifyPhone(String phone, String code, String username) {
        throw new BadRequestException("Mobile verification is temporarily disabled. Please use email verification.");
    }

    public AuthResponse loginWithOtp(String identifier, String code) {
        User user = findByEmailOrPhone(identifier);
        if (user == null) {
            throw new BadRequestException("No account found");
        }
        String id = identifier.contains("@") ? user.getEmail() : user.getPhone();
        otpService.verifyOtp(id, OtpPurpose.LOGIN, code);
        return issueTokens(user);
    }

    @Transactional
    public void forgotPassword(String identifier) {
        User user = findByEmailOrPhone(identifier);
        if (user == null) {
            // Do not reveal whether account exists
            return;
        }
        String id = identifier.contains("@") ? user.getEmail() : user.getPhone();
        otpService.sendOtp(id, OtpPurpose.RESET_PASSWORD);
    }

    @Transactional
    public void resetPassword(String identifier, String code, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }
        User user = findByEmailOrPhone(identifier);
        if (user == null) {
            throw new BadRequestException("Invalid request");
        }
        String id = identifier.contains("@") ? user.getEmail() : user.getPhone();
        otpService.verifyOtp(id, OtpPurpose.RESET_PASSWORD, code);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAllUserTokens(user);
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new BadRequestException("New password must be at least 8 characters");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAllUserTokens(user);
    }

    private User findByEmailOrPhone(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        String id = identifier.trim();
        if (id.contains("@")) {
            return userRepository.findByEmail(id.toLowerCase()).orElse(null);
        }
        String phone = normalizePhone(id);
        if (phone == null) return null;
        return userRepository.findByPhone(phone).orElse(null);
    }

    private OtpPurpose parsePurpose(String purpose) {
        try {
            return OtpPurpose.valueOf(purpose.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid OTP purpose");
        }
    }
}

