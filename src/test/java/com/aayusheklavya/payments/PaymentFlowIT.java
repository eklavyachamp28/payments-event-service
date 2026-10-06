package com.aayusheklavya.payments;

import com.aayusheklavya.payments.api.CreatePaymentRequest;
import com.aayusheklavya.payments.api.PaymentResponse;
import com.aayusheklavya.payments.domain.LedgerEntry;
import com.aayusheklavya.payments.domain.LedgerEntryRepository;
import com.aayusheklavya.payments.domain.PaymentStatus;
import com.aayusheklavya.payments.events.PaymentInitiatedEvent;
import com.aayusheklavya.payments.events.Topics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end flow: REST -> PaymentInitiated on Kafka -> consumer settles -> ledger + status.
 * Runs against embedded Kafka and an in-memory PostgreSQL-mode database, so it needs no Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = {Topics.PAYMENTS_INITIATED, Topics.PAYMENTS_SETTLED, Topics.PAYMENTS_INITIATED_DLT})
@ActiveProfiles("test")
class PaymentFlowIT {

    @Autowired TestRestTemplate rest;
    @Autowired LedgerEntryRepository ledger;
    @Autowired KafkaTemplate<String, Object> kafka;

    @Test
    void paymentIsSettledThroughKafkaAndLedgerIsBalanced() {
        ResponseEntity<PaymentResponse> created = post("key-" + UUID.randomUUID(),
                new CreatePaymentRequest("m-1", "c-1", new BigDecimal("150.25"), "inr"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse body = created.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(body.currency()).isEqualTo("INR");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            PaymentResponse current = rest.getForObject("/api/payments/" + body.id(), PaymentResponse.class);
            assertThat(current.status()).isEqualTo(PaymentStatus.SETTLED);
        });

        List<LedgerEntry> entries = ledger.findByPaymentId(body.id());
        assertThat(entries).hasSize(2);
        BigDecimal debits = sum(entries, LedgerEntry.Direction.DEBIT);
        BigDecimal credits = sum(entries, LedgerEntry.Direction.CREDIT);
        assertThat(debits).isEqualByComparingTo(credits).isEqualByComparingTo("150.25");
        assertThat(entries).extracting(LedgerEntry::getAccountId).containsExactlyInAnyOrder("customer:c-1", "merchant:m-1");
    }

    @Test
    void sameIdempotencyKeyReturnsSamePaymentWithoutCreatingAnother() {
        String key = "key-" + UUID.randomUUID();
        CreatePaymentRequest req = new CreatePaymentRequest("m-2", "c-2", new BigDecimal("10.00"), "USD");

        ResponseEntity<PaymentResponse> first = post(key, req);
        ResponseEntity<PaymentResponse> replay = post(key, req);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().id()).isEqualTo(first.getBody().id());
    }

    @Test
    void redeliveredEventDoesNotPostLedgerTwice() {
        ResponseEntity<PaymentResponse> created = post("key-" + UUID.randomUUID(),
                new CreatePaymentRequest("m-3", "c-3", new BigDecimal("42.00"), "EUR"));
        UUID paymentId = created.getBody().id();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(ledger.findByPaymentId(paymentId)).hasSize(2));

        // Simulate the broker redelivering the original event (same eventId) and a second, distinct
        // initiated event for a payment that is already final. Neither may post more ledger lines.
        UUID originalEventId = UUID.randomUUID();
        PaymentInitiatedEvent dup = new PaymentInitiatedEvent(originalEventId, paymentId, "m-3", "c-3",
                new BigDecimal("42.00"), "EUR", Instant.now());
        kafka.send(Topics.PAYMENTS_INITIATED, paymentId.toString(), dup);
        kafka.send(Topics.PAYMENTS_INITIATED, paymentId.toString(), dup);
        kafka.flush();

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(ledger.findByPaymentId(paymentId)).hasSize(2));
    }

    @Test
    void rejectsInvalidRequestAndMissingKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> noKey = rest.exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(new CreatePaymentRequest("m", "c", BigDecimal.ONE, "INR"), headers), String.class);
        assertThat(noKey.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> bad = rest.exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(new CreatePaymentRequest("", "c", new BigDecimal("-5"), "RUPEES"), withKey("k")), String.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bad.getBody()).contains("merchantId").contains("amount").contains("currency");
    }

    private ResponseEntity<PaymentResponse> post(String key, CreatePaymentRequest req) {
        return rest.exchange("/api/payments", HttpMethod.POST, new HttpEntity<>(req, withKey(key)), PaymentResponse.class);
    }

    private static HttpHeaders withKey(String key) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Idempotency-Key", key);
        return h;
    }

    private static BigDecimal sum(List<LedgerEntry> entries, LedgerEntry.Direction d) {
        return entries.stream().filter(e -> e.getDirection() == d).map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
