package com.aayusheklavya.payments.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published after a payment is accepted via the API and persisted as PENDING. */
public record PaymentInitiatedEvent(
        UUID eventId,
        UUID paymentId,
        String merchantId,
        String customerId,
        BigDecimal amount,
        String currency,
        Instant occurredAt) {

    public static final String TYPE = "PaymentInitiated";
}
