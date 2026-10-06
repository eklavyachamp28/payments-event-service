CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    merchant_id     VARCHAR(64)  NOT NULL,
    customer_id     VARCHAR(64)  NOT NULL,
    amount          NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3)   NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_payments_merchant_created ON payments (merchant_id, created_at DESC);

CREATE TABLE ledger_entries (
    id          UUID PRIMARY KEY,
    payment_id  UUID NOT NULL REFERENCES payments (id),
    account_id  VARCHAR(64)  NOT NULL,
    direction   VARCHAR(8)   NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount      NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency    VARCHAR(3)   NOT NULL,
    posted_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ledger_payment ON ledger_entries (payment_id);
CREATE INDEX idx_ledger_account ON ledger_entries (account_id, posted_at DESC);

-- Consumer inbox: one row per processed Kafka event, keyed by event id.
CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
