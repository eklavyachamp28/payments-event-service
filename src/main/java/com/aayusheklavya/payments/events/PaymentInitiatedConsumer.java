package com.aayusheklavya.payments.events;

import com.aayusheklavya.payments.service.SettlementService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentInitiatedConsumer {

    private final SettlementService settlement;

    public PaymentInitiatedConsumer(SettlementService settlement) {
        this.settlement = settlement;
    }

    @KafkaListener(topics = Topics.PAYMENTS_INITIATED, groupId = "${payments.consumer-group}")
    public void onPaymentInitiated(PaymentInitiatedEvent event) {
        settlement.settle(event);
    }
}
