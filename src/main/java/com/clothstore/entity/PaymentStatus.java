package com.clothstore.entity;

/**
 * PENDING  — nothing collected yet (prepaid awaiting gateway, or COD before platform fee).
 * PARTIAL  — COD platform fee (₹99) paid online; remaining due at delivery / online.
 * PAID     — full order amount collected (prepaid online, or COD cash + platform fee).
 * FAILED   — last payment attempt failed.
 */
public enum PaymentStatus {
    PENDING,
    PARTIAL,
    PAID,
    FAILED
}
