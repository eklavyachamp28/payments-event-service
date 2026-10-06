package com.aayusheklavya.payments.service;

import com.aayusheklavya.payments.api.CreatePaymentRequest;
import com.aayusheklavya.payments.domain.Payment;
import com.aayusheklavya.payments.domain.PaymentRepository;
import com.aayusheklavya.payments.events.PaymentEventPublisher;
import com.aayusheklavya.payments.events.PaymentInitiatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentEventPublisher publisher;

    public PaymentService(PaymentRepository payments, PaymentEventPublisher publisher) {
        this.payments = payments;
        this.publisher = publisher;
    }

    /**
     * Idempotent create: the same Idempotency-Key always returns the same payment, so a client
     * that retries after a timeout never charges a customer twice.
     */
    @Transactional
    public Payment initiate(String idempotencyKey, CreatePaymentRequest request) {
        Optional<Payment> existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent replay for key {} -> payment {}", idempotencyKey, existing.get().getId());
            return existing.get();
        }

        Payment payment = Payment.initiate(idempotencyKey, request.merchantId(), request.customerId(),
                request.amount(), request.currency().toUpperCase());
        try {
            payment = payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException race) {
            // Two concurrent requests with the same key: the loser re-reads the winner's row.
            return payments.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> race);
        }

        PaymentInitiatedEvent event = new PaymentInitiatedEvent(UUID.randomUUID(), payment.getId(),
                payment.getMerchantId(), payment.getCustomerId(), payment.getAmount(), payment.getCurrency(),
                Instant.now());

        // Publish only once the row is committed, so a consumer can never see an event for a payment
        // that does not exist yet.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publishInitiated(event);
            }
        });
        return payment;
    }

    @Transactional(readOnly = true)
    public Optional<Payment> find(UUID id) {
        return payments.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> find(String idempotencyKey) {
        return payments.findByIdempotencyKey(idempotencyKey);
    }
}
