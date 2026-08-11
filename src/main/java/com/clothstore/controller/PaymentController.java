package com.clothstore.controller;

import com.clothstore.dto.*;
import com.clothstore.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** Create Razorpay/mock payment session for an existing order */
    @PostMapping("/create/{orderId}")
    public ResponseEntity<ApiResponse<PaymentSessionDto>> create(
            @PathVariable Long orderId,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.createSession(orderId, auth.getName())));
    }

    /**
     * Verify payment and mark the order PAID.
     *
     * <p>For real Razorpay flows this returns <strong>202 Accepted</strong> immediately
     * — the actual signature verification + DB write happens on the
     * {@code paymentVerifyExecutor} worker pool. The browser is expected to
     * redirect to {@code /orders} in parallel and poll
     * {@code GET /orders/recent-paid/{id}} until the order flips to PAID.</p>
     *
     * <p>For mock flows ({@code razorpay.mode: mock}) the call is synchronous so
     * the existing UX (one tap → instant PAID) keeps working without polling.</p>
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<OrderDto>> verify(
            @RequestBody PaymentVerifyRequest request,
            Authentication auth) {
        boolean async = !paymentService.isMockMode() && !Boolean.TRUE.equals(request.getMockConfirm());
        if (async) {
            paymentService.verifyAndConfirmAsync(auth.getName(), request);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.ok("Payment is being verified", null));
        }
        // Mock / force-confirm: synchronous. Order is PAID before this returns.
        OrderDto dto = paymentService.verifyAndConfirm(auth.getName(), request);
        return ResponseEntity.ok(ApiResponse.ok("Payment successful", dto));
    }
}