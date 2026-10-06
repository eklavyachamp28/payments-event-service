package com.aayusheklavya.payments.service;

import com.aayusheklavya.payments.domain.*;
import com.aayusheklavya.payments.events.PaymentEventPublisher;
import com.aayusheklavya.payments.events.PaymentInitiatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock PaymentRepository payments;
    @Mock LedgerEntryRepository ledger;
    @Mock ProcessedEventRepository processedEvents;
    @Mock PaymentEventPublisher publisher;
    @InjectMocks SettlementService service;

    @Test
    void duplicateEventIsIgnoredWithoutTouchingLedger() {
        PaymentInitiatedEvent event = event(UUID.randomUUID());
        when(processedEvents.existsById(event.eventId())).thenReturn(true);

        assertThat(service.settle(event)).isEqualTo(SettlementService.SettlementResult.DUPLICATE);

        verifyNoInteractions(ledger, publisher);
        verify(payments, never()).findById(any());
    }

    @Test
    void pendingPaymentGetsTwoLedgerLinesAndSettledEvent() {
        Payment payment = Payment.initiate("k", "m-1", "c-1", new BigDecimal("99.99"), "INR");
        PaymentInitiatedEvent event = event(payment.getId());
        when(processedEvents.existsById(event.eventId())).thenReturn(false);
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThat(service.settle(event)).isEqualTo(SettlementService.SettlementResult.SETTLED);

        verify(ledger, times(2)).save(any(LedgerEntry.class));
        verify(processedEvents).save(any(ProcessedEvent.class));
        verify(publisher).publishSettled(any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SETTLED);
    }

    @Test
    void alreadyFinalPaymentIsRecordedButNotRePosted() {
        Payment payment = Payment.initiate("k", "m-1", "c-1", new BigDecimal("5"), "INR");
        payment.markFailed("card declined");
        PaymentInitiatedEvent event = event(payment.getId());
        when(processedEvents.existsById(event.eventId())).thenReturn(false);
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));

        assertThat(service.settle(event)).isEqualTo(SettlementService.SettlementResult.ALREADY_FINAL);

        verifyNoInteractions(ledger, publisher);
        verify(processedEvents).save(any(ProcessedEvent.class));
    }

    private static PaymentInitiatedEvent event(UUID paymentId) {
        return new PaymentInitiatedEvent(UUID.randomUUID(), paymentId, "m-1", "c-1", new BigDecimal("1"), "INR", Instant.now());
    }
}
