package com.aayusheklavya.payments.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published once ledger entries for a payment have been posted. */
public record PaymentSettledEvent(
        UUID eventId,
        UUID paymentId,
        String merchantId,
        BigDecimal amount,
        String currency,
        Instant settledAt) {

    public static final String TYPE = "PaymentSettled";
}
