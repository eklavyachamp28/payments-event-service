package com.aayusheklavya.payments.service;

import com.aayusheklavya.payments.domain.*;
import com.aayusheklavya.payments.events.PaymentEventPublisher;
import com.aayusheklavya.payments.events.PaymentInitiatedEvent;
import com.aayusheklavya.payments.events.PaymentSettledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Settles a payment by posting double-entry ledger lines. The processed_events insert and the ledger
 * writes share one transaction, so a redelivered Kafka message either finds the inbox row (and is
 * skipped) or rolls back entirely. That is what makes the consumer idempotent.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final PaymentRepository payments;
    private final LedgerEntryRepository ledger;
    private final ProcessedEventRepository processedEvents;
    private final PaymentEventPublisher publisher;

    public SettlementService(PaymentRepository payments, LedgerEntryRepository ledger,
                             ProcessedEventRepository processedEvents, PaymentEventPublisher publisher) {
        this.payments = payments;
        this.ledger = ledger;
        this.processedEvents = processedEvents;
        this.publisher = publisher;
    }

    @Transactional
    public SettlementResult settle(PaymentInitiatedEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Duplicate event {} for payment {} ignored", event.eventId(), event.paymentId());
            return SettlementResult.DUPLICATE;
        }

        Payment payment = payments.findById(event.paymentId())
                .orElseThrow(() -> new IllegalStateException("Payment not found: " + event.paymentId()));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            processedEvents.save(new ProcessedEvent(event.eventId(), PaymentInitiatedEvent.TYPE));
            return SettlementResult.ALREADY_FINAL;
        }

        ledger.save(new LedgerEntry(payment.getId(), "customer:" + payment.getCustomerId(),
                LedgerEntry.Direction.DEBIT, payment.getAmount(), payment.getCurrency()));
        ledger.save(new LedgerEntry(payment.getId(), "merchant:" + payment.getMerchantId(),
                LedgerEntry.Direction.CREDIT, payment.getAmount(), payment.getCurrency()));
        payment.markSettled();
        processedEvents.save(new ProcessedEvent(event.eventId(), PaymentInitiatedEvent.TYPE));

        publisher.publishSettled(new PaymentSettledEvent(UUID.randomUUID(), payment.getId(),
                payment.getMerchantId(), payment.getAmount(), payment.getCurrency(), Instant.now()));
        log.info("Payment {} settled: {} {}", payment.getId(), payment.getAmount(), payment.getCurrency());
        return SettlementResult.SETTLED;
    }

    public enum SettlementResult { SETTLED, DUPLICATE, ALREADY_FINAL }
}
