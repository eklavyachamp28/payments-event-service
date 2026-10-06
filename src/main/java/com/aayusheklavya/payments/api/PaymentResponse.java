package com.aayusheklavya.payments.api;

import com.aayusheklavya.payments.domain.Payment;
import com.aayusheklavya.payments.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID id, String merchantId, String customerId, BigDecimal amount, String currency,
                              PaymentStatus status, String failureReason, Instant createdAt, Instant updatedAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getMerchantId(), p.getCustomerId(), p.getAmount(), p.getCurrency(),
                p.getStatus(), p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
