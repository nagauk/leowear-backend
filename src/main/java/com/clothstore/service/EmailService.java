package com.clothstore.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email delivery for Leo Wear.
 *
 * Recommended free / low-cost providers (pick one):
 * 1) Gmail SMTP — free, ~500/day. Create App Password at Google Account → Security.
 * 2) Brevo (Sendinblue) — free 300 emails/day. https://www.brevo.com/
 * 3) Resend — free 3,000/month. https://resend.com/
 * 4) Amazon SES — very cheap after free tier.
 *
 * Set spring.mail.* and app.mail.enabled=true in application.yml.
 * When disabled or misconfigured, messages are logged only (safe for local dev).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:noreply@leowear.local}")
    private String from;

    @Value("${app.mail.from-name:Leo Wear}")
    private String fromName;

    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank() || !to.contains("@")) {
            log.warn("Skip email — invalid address: {}", to);
            return;
        }
        if (!enabled) {
            log.info("════════════════════════════════════════");
            log.info(" EMAIL [MOCK] to={} subject={}", to, subject);
            log.info(" {}", body.replace("\n", " | "));
            log.info("════════════════════════════════════════");
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromName + " <" + from + ">");
            msg.setTo(to.trim());
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Email sent to {} subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            // Fallback log so OTP is never lost in dev
            log.info(" EMAIL FALLBACK to={} subject={} body={}", to, subject, body);
        }
    }

    public void sendOtp(String email, String purpose, String code) {
        String subject;
        String body;
        switch (purpose) {
            case "EMAIL_VERIFY" -> {
                subject = "Leo Wear — Verify your email";
                body = "Your email verification OTP is: " + code + "\n\nValid for 10 minutes.\n\n— Leo Wear";
            }
            case "RESET_PASSWORD" -> {
                subject = "Leo Wear — Password reset OTP";
                body = "Your password reset OTP is: " + code + "\n\nValid for 10 minutes. If you did not request this, ignore this email.\n\n— Leo Wear";
            }
            case "LOGIN" -> {
                subject = "Leo Wear — Login OTP";
                body = "Your login OTP is: " + code + "\n\nValid for 10 minutes.\n\n— Leo Wear";
            }
            default -> {
                subject = "Leo Wear — OTP";
                body = "Your OTP is: " + code + "\n\nValid for 10 minutes.\n\n— Leo Wear";
            }
        }
        send(email, subject, body);
    }

    public void sendOrderStatus(String email, String customerName, String orderNumber,
                                String status, String totalAmount) {
        String subject = "Leo Wear — Order " + orderNumber + " is now " + status;
        String body = "Hi " + (customerName != null ? customerName : "there") + ",\n\n"
                + "Your order " + orderNumber + " status has been updated to: " + status + ".\n"
                + (totalAmount != null ? "Order total: ₹" + totalAmount + "\n" : "")
                + "\nTrack your orders anytime in My Orders on Leo Wear.\n\nThank you for shopping with us!\n— Leo Wear Team";
        send(email, subject, body);
    }
}
