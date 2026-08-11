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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * Razorpay payment integration for Leo Wear.
 *
 * <p>Performance notes:
 * <ul>
 *   <li>Razorpay order creation is outside a long DB transaction (no connection held during HTTP).</li>
 *   <li>Shared RestClient with connect/read timeouts for predictable latency.</li>
 * </ul>
 */
@Service
@Slf4j
public class PaymentService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final RestClient razorpayRestClient;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(OrderRepository orderRepository,
                          @Lazy OrderService orderService,
                          @Qualifier("razorpayRestClient") RestClient razorpayRestClient,
                          ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.razorpayRestClient = razorpayRestClient;
        this.eventPublisher = eventPublisher;
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

    /**
     * Build a payment session. DB load is brief; Razorpay HTTP runs without holding a transaction.
     */
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
            // External call — not inside @Transactional so DB pool is not blocked
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
                .codPlatformFee(codAdvance)
                .message(message)
                .build();
    }

    /**
     * Verify payment and mark the order PAID. Runs on the
     * {@code paymentVerifyExecutor} so the HTTP request returns immediately —
     * the customer's browser can redirect to {@code /orders} in parallel and
     * poll {@code GET /orders/recent-paid/{id}} until the order flips to PAID.
     *
     * <p>Mock mode is handled synchronously (returns the same call) because
     * the mock "payment" is instantaneous and we want the redirect to land on
     * an already-PAID order.</p>
     *
     * <p>Signature verification errors throw {@link BadRequestException} which
     * is logged on the worker thread — the HTTP caller has already moved on,
     * but the {@link com.clothstore.config.AuditFilter} records the request.</p>
     */
    @Async("paymentVerifyExecutor")
    public void verifyAndConfirmAsync(String username, PaymentVerifyRequest req) {
        try {
            OrderDto dto = doVerifyAndConfirm(username, req);
            if (dto != null) {
                eventPublisher.publishEvent(new PaymentVerifiedEvent(
                        dto.getId(),
                        username,
                        dto.getPaymentStatus()));
            }
        } catch (Exception ex) {
            log.error("Async payment verify failed for order {}: {}",
                    req == null ? null : req.getOrderId(), ex.getMessage(), ex);
        }
    }

    /** Mock-mode entry: synchronous verify because there's no IO to wait for. */
    @Transactional
    public OrderDto verifyAndConfirm(String username, PaymentVerifyRequest req) {
        return doVerifyAndConfirm(username, req);
    }

    /**
     * Core verify+confirm logic shared by both entry points.
     */
    private OrderDto doVerifyAndConfirm(String username, PaymentVerifyRequest req) {
        if (req == null || req.getOrderId() == null) {
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
            Map<String, Object> body = Map.of(
                    "amount", amountPaise,
                    "currency", currency,
                    "receipt", order.getOrderNumber(),
                    "payment_capture", 1
            );

            long t0 = System.currentTimeMillis();
            Map<String, Object> resp = razorpayRestClient.post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBasicAuth(keyId, keySecret))
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            log.info("Razorpay order created for {} in {}ms", order.getOrderNumber(), System.currentTimeMillis() - t0);

            if (resp == null || resp.get("id") == null) {
                throw new BadRequestException("Failed to create Razorpay order");
            }
            return String.valueOf(resp.get("id"));
        } catch (BadRequestException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Razorpay order create failed: {}", e.getMessage());
            throw new BadRequestException("Payment gateway is slow or unavailable. Please try again.");
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
