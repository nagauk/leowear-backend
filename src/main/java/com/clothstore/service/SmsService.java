package com.clothstore.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * SMS / OTP delivery.
 *
 * CURRENTLY DISABLED — Leo Wear uses email OTP only.
 * To re-enable: set app.sms.enabled=true, choose provider, uncomment send path in sendOtp().
 *
 * Providers: MSG91 (India), Twilio (global).
 */
@Service
@Slf4j
public class SmsService {

    @Value("${app.sms.enabled:false}")
    private boolean enabled;

    @Value("${app.sms.provider:mock}")
    private String provider;

    @Value("${app.sms.msg91.auth-key:}")
    private String msg91AuthKey;

    @Value("${app.sms.msg91.template-id:}")
    private String msg91TemplateId;

    @Value("${app.sms.msg91.sender-id:LEOWR}")
    private String msg91SenderId;

    @Value("${app.sms.twilio.account-sid:}")
    private String twilioSid;

    @Value("${app.sms.twilio.auth-token:}")
    private String twilioToken;

    @Value("${app.sms.twilio.from-number:}")
    private String twilioFrom;

    public void sendOtp(String phone, String purpose, String code) {
        // Mobile SMS temporarily disabled — keep email OTP only
        log.info("SMS disabled — OTP for phone={} purpose={} not delivered via SMS", phone, purpose);
        log.info("════════════════════════════════════════");
        log.info(" SMS [DISABLED] OTP={} (configure app.sms.enabled when ready)", code);
        log.info("════════════════════════════════════════");
        // When re-enabling SMS, remove the early return and use the block below.
        if (true) {
            return;
        }

        String normalized = normalizePhone(phone);
        String message = buildMessage(purpose, code);
        if (!enabled || "mock".equalsIgnoreCase(provider)) {
            log.info("SMS [MOCK] to={} OTP={}", normalized, code);
            return;
        }
        try {
            if ("msg91".equalsIgnoreCase(provider)) {
                sendMsg91(normalized, code, message);
            } else if ("twilio".equalsIgnoreCase(provider)) {
                sendTwilio(normalized, message);
            }
        } catch (Exception e) {
            log.error("SMS send failed: {}", e.getMessage());
        }
    }

    private String buildMessage(String purpose, String code) {
        return "Leo Wear OTP: " + code + ". Valid 10 min.";
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 10) return "91" + digits;
        return digits;
    }

    private void sendMsg91(String phone91, String code, String message) {
        RestTemplate rt = new RestTemplate();
        if (msg91TemplateId != null && !msg91TemplateId.isBlank() && !msg91TemplateId.contains("REPLACE")) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("authkey", msg91AuthKey);
            Map<String, Object> body = Map.of(
                    "template_id", msg91TemplateId,
                    "short_url", "0",
                    "recipients", java.util.List.of(Map.of("mobiles", phone91, "otp", code))
            );
            rt.exchange("https://control.msg91.com/api/v5/flow/", HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
            return;
        }
        String legacy = UriComponentsBuilder
                .fromHttpUrl("https://api.msg91.com/api/sendhttp.php")
                .queryParam("authkey", msg91AuthKey)
                .queryParam("mobiles", phone91)
                .queryParam("message", message)
                .queryParam("sender", msg91SenderId)
                .queryParam("route", "4")
                .queryParam("country", "91")
                .toUriString();
        rt.getForObject(legacy, String.class);
    }

    private void sendTwilio(String phone91, String message) {
        RestTemplate rt = new RestTemplate();
        String to = phone91.startsWith("+") ? phone91 : "+" + phone91;
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioSid + "/Messages.json";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(twilioSid, twilioToken);
        String body = "To=" + java.net.URLEncoder.encode(to, java.nio.charset.StandardCharsets.UTF_8)
                + "&From=" + java.net.URLEncoder.encode(twilioFrom, java.nio.charset.StandardCharsets.UTF_8)
                + "&Body=" + java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
        rt.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }
}
