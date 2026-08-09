package com.clothstore.service;

import com.clothstore.dto.OrderDto;
import com.clothstore.dto.PaymentSessionDto;
import com.clothstore.dto.PaymentVerifyRequest;
import com.clothstore.entity.Order;
import com.clothstore.entity.PaymentMethod;
import com.clothstore.entity.PaymentStatus;
import com.clothstore.exception.BadRequestException;
import com.clothstore.exception.ResourceNotFoundException;
import com.clothstore.repository.OrderRepository;
import org.springframework.context.annotation.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * Razorpay payment integration for Leo Wear.
 *
 * <p>TODO — production setup:
 * <ol>
 *   <li>Create account at https://dashboard.razorpay.com/</li>
 *   <li>Settings → API Keys → Generate Test/Live keys</li>
 *   <li>Set razorpay.key-id, razorpay.key-secret, razorpay.mode=test|live in application.yml
 *       (or env RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_MODE)</li>
 *   <li>Enable UPI, Cards, NetBanking in Razorpay Dashboard → Payment Methods</li>
 * </ol>
 * Until real keys are set (mode=mock or key contains REPLACE), checkout uses a safe mock UI.
 */
@Service
@Slf4j
public class PaymentService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public PaymentService(OrderRepository orderRepository, @Lazy OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Value("${razorpay.mode:mock}")
    private String mode;

    @Value("${razorpay.currency:INR}")
    private String currency;

    @Value("${razorpay.company-name:Leo Wear}")
    private String companyName;

    public boolean isMockMode() {
        String m = mode == null ? "mock" : mode.trim().toLowerCase();
        if ("mock".equals(m)) return true;
        return keyId == null || keyId.contains("REPLACE") || keySecret == null || keySecret.contains("REPLACE");
    }

    @Transactional(readOnly = true)
    public PaymentSessionDto createSession(Long orderId, String username) {
        Order order = loadOwnedOrder(orderId, username);

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BadRequestException("Order is already paid");
        }

        boolean isCod = order.getPaymentMethod() == PaymentMethod.COD;
        boolean codAdvance = false;
        boolean codRemaining = false;
        BigDecimal chargeAmount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;

        if (isCod) {
            if (order.getPaymentStatus() == PaymentStatus.PARTIAL) {
                // Collect remaining balance (total − advance already paid)
                BigDecimal paid = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
                if (paid.compareTo(BigDecimal.ZERO) <= 0 && order.getPlatformCharge() != null) {
                    paid = order.getPlatformCharge();
                }
                chargeAmount = chargeAmount.subtract(paid);
                if (chargeAmount.compareTo(BigDecimal.ZERO) < 0) {
                    chargeAmount = BigDecimal.ZERO;
                }
                codRemaining = true;
            } else {
                // First online payment: COD advance (reduces amount due at delivery)
                BigDecimal advance = order.getPlatformCharge() != null
                        ? order.getPlatformCharge()
                        : new BigDecimal("99");
                if (advance.compareTo(BigDecimal.ZERO) <= 0) {
                    advance = new BigDecimal("99");
                }
                chargeAmount = advance;
                codAdvance = true;
            }
        }

        long amountPaise = chargeAmount
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        if (amountPaise < 100) {
            throw new BadRequestException("Order amount too low for online payment");
        }

        String rzpOrderId = null;
        boolean mock = isMockMode();

        if (!mock) {
            rzpOrderId = createRazorpayOrder(order, amountPaise);
        } else {
            log.info("Payment MOCK session for order {} amount ₹{} (codAdvance={}, codRemaining={})",
                    order.getOrderNumber(), chargeAmount, codAdvance, codRemaining);
        }

        String message;
        if (mock) {
            if (codAdvance) {
                message = "Mock payment — COD advance ₹" + chargeAmount + " (deducted from order total). Remaining at delivery.";
            } else if (codRemaining) {
                message = "Mock payment — remaining COD balance ₹" + chargeAmount + ".";
            } else {
                message = "Mock payment mode — set razorpay.key-id / key-secret to enable real Razorpay.";
            }
        } else if (codAdvance) {
            message = "Pay advance ₹" + chargeAmount + " online. This amount is deducted from your order total; pay the rest on delivery.";
        } else if (codRemaining) {
            message = "Pay remaining balance ₹" + chargeAmount + " online.";
        } else {
            message = "Complete payment via Razorpay Checkout";
        }

        return PaymentSessionDto.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .amount(chargeAmount)
                .currency(currency)
                .keyId(mock ? null : keyId)
                .razorpayOrderId(rzpOrderId)
                .companyName(companyName)
                .customerName(order.getUser().getFullName())
                .customerEmail(order.getUser().getEmail())
                .customerPhone(order.getPhone() != null ? order.getPhone() : order.getUser().getPhone())
                .mock(mock)
                .codPlatformFee(codAdvance) // flag still means "COD advance session"
                .message(message)
                .build();
    }

    @Transactional
    public OrderDto verifyAndConfirm(String username, PaymentVerifyRequest req) {
        if (req.getOrderId() == null) {
            throw new BadRequestException("orderId is required");
        }
        Order order = loadOwnedOrder(req.getOrderId(), username);

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return orderService.toDto(order);
        }

        if (isMockMode() && Boolean.TRUE.equals(req.getMockConfirm())) {
            String ref = "MOCK-" + System.currentTimeMillis();
            log.info("Mock payment confirmed for order {} ref {}", order.getOrderNumber(), ref);
            return orderService.markPaid(order.getId(), username, ref, false);
        }

        if (req.getRazorpayOrderId() == null || req.getRazorpayPaymentId() == null || req.getRazorpaySignature() == null) {
            throw new BadRequestException("Missing Razorpay payment fields");
        }

        if (!verifySignature(req.getRazorpayOrderId(), req.getRazorpayPaymentId(), req.getRazorpaySignature())) {
            throw new BadRequestException("Payment signature verification failed");
        }

        return orderService.markPaid(order.getId(), username, req.getRazorpayPaymentId(), false);
    }

    private Order loadOwnedOrder(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getUser().getUsername().equals(username)) {
            throw new BadRequestException("You cannot pay for this order");
        }
        return order;
    }

    @SuppressWarnings("unchecked")
    private String createRazorpayOrder(Order order, long amountPaise) {
        try {
            RestTemplate rt = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(keyId, keySecret);

            Map<String, Object> body = Map.of(
                    "amount", amountPaise,
                    "currency", currency,
                    "receipt", order.getOrderNumber(),
                    "payment_capture", 1
            );

            ResponseEntity<Map> resp = rt.exchange(
                    "https://api.razorpay.com/v1/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            if (resp.getBody() == null || resp.getBody().get("id") == null) {
                throw new BadRequestException("Failed to create Razorpay order");
            }
            return String.valueOf(resp.getBody().get("id"));
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Razorpay order create failed: {}", e.getMessage());
            throw new BadRequestException("Payment gateway error: " + e.getMessage());
        }
    }

    private boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(hash);
            return expected.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("Signature verify error: {}", e.getMessage());
            return false;
        }
    }
}
