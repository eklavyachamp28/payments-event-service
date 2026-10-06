package com.aayusheklavya.payments.api;

import com.aayusheklavya.payments.domain.Payment;
import com.aayusheklavya.payments.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /**
     * Accepts a payment. Clients must send an Idempotency-Key header; replaying the same key returns
     * 200 with the original payment instead of creating a second one.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                  @Valid @RequestBody CreatePaymentRequest request) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be 1-128 characters");
        }
        boolean existed = payments.find(idempotencyKey).isPresent();
        Payment payment = payments.initiate(idempotencyKey, request);
        PaymentResponse body = PaymentResponse.from(payment);
        return existed
                ? ResponseEntity.ok(body)
                : ResponseEntity.created(URI.create("/api/payments/" + payment.getId())).body(body);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return payments.find(id).map(PaymentResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment not found"));
    }
}
