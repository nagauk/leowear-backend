package com.clothstore.service;

/**
 * Published by {@link PaymentService} after a successful async payment
 * verification. Currently informational (logged by AuditFilter); future
 * listeners can hook in for analytics, receipts, or downstream workflows
 * without touching the payment path.
 */
public record PaymentVerifiedEvent(Long orderId, String username, String paymentStatus) {
}