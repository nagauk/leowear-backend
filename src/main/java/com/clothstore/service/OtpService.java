package com.clothstore.service;

import com.clothstore.entity.OtpPurpose;
import com.clothstore.entity.OtpToken;
import com.clothstore.exception.BadRequestException;
import com.clothstore.repository.OtpTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final int OTP_TTL_MINUTES = 10;
    private final OtpTokenRepository otpRepository;
    private final EmailService emailService;
    private final SmsService smsService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public String sendOtp(String identifier, OtpPurpose purpose) {
        String id = normalize(identifier);
        if (id.isBlank()) {
            throw new BadRequestException("Email or phone is required");
        }
        otpRepository.invalidateAll(id, purpose);

        String code = String.format("%06d", random.nextInt(1_000_000));
        OtpToken token = OtpToken.builder()
                .identifier(id)
                .code(code)
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .used(false)
                .build();
        otpRepository.save(token);

        String purposeName = purpose.name();
        if (id.contains("@")) {
            emailService.sendOtp(id, purposeName, code);
        } else {
            smsService.sendOtp(id, purposeName, code);
        }

        // Always log for local QA
        log.info("OTP [{}] generated for {} (valid {} min)", purpose, id, OTP_TTL_MINUTES);

        return code;
    }

    @Transactional
    public void verifyOtp(String identifier, OtpPurpose purpose, String code) {
        String id = normalize(identifier);
        if (code == null || code.isBlank()) {
            throw new BadRequestException("OTP is required");
        }
        OtpToken token = otpRepository
                .findTopByIdentifierAndPurposeAndUsedFalseOrderByCreatedAtDesc(id, purpose)
                .orElseThrow(() -> new BadRequestException("No OTP found. Please request a new one."));

        if (!token.isValid(code.trim())) {
            if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new BadRequestException("OTP has expired. Please request a new one.");
            }
            throw new BadRequestException("Invalid OTP. Please try again.");
        }
        token.setUsed(true);
        otpRepository.save(token);
    }

    private String normalize(String identifier) {
        if (identifier == null) return "";
        String id = identifier.trim();
        if (id.contains("@")) {
            return id.toLowerCase();
        }
        return id.replaceAll("[^0-9+]", "");
    }
}
