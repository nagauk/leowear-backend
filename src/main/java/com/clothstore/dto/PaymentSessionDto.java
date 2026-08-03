package com.clothstore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSessionDto {
    private Long orderId;
    private String orderNumber;
    private BigDecimal amount;
    private String currency;
    /** Razorpay key id (safe to expose to browser) */
    private String keyId;
    /** Razorpay order id — empty in pure mock mode */
    private String razorpayOrderId;
    private String companyName;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    /** true = use in-app mock pay button (no Razorpay keys configured yet) */
    private boolean mock;
    private String message;
}
