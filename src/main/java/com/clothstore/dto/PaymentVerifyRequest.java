package com.clothstore.dto;

import lombok.Data;

@Data
public class PaymentVerifyRequest {
    private Long orderId;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;
    /** When true and gateway is in mock mode, mark paid without signature */
    private Boolean mockConfirm;
}
