package com.aayusheklavya.payments.events;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Keyed by paymentId so all events for one payment land on the same partition, in order. */
    public void publishInitiated(PaymentInitiatedEvent event) {
        kafkaTemplate.send(Topics.PAYMENTS_INITIATED, event.paymentId().toString(), event);
    }

    public void publishSettled(PaymentSettledEvent event) {
        kafkaTemplate.send(Topics.PAYMENTS_SETTLED, event.paymentId().toString(), event);
    }
}
