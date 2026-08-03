package com.clothstore.controller;

import com.clothstore.dto.*;
import com.clothstore.service.PaymentService;
import lombok.RequiredArgsConstructor;
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

    /** Verify Razorpay signature (or mock confirm) and mark order paid */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<OrderDto>> verify(
            @RequestBody PaymentVerifyRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("Payment successful",
                paymentService.verifyAndConfirm(auth.getName(), request)));
    }
}
