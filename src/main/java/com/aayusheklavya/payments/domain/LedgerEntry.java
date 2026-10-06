package com.aayusheklavya.payments.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Double-entry ledger line. Every settled payment produces exactly two entries:
 * a DEBIT on the customer account and a CREDIT on the merchant account.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    public enum Direction { DEBIT, CREDIT }

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Direction direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    protected LedgerEntry() {}

    public LedgerEntry(UUID paymentId, String accountId, Direction direction, BigDecimal amount, String currency) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.postedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPaymentId() { return paymentId; }
    public String getAccountId() { return accountId; }
    public Direction getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getPostedAt() { return postedAt; }
}
