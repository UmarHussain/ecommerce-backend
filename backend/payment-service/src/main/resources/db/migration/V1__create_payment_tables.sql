CREATE TABLE payment_attempt (
    operation_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    saga_id UUID,
    correlation_id VARCHAR(200),
    request_event_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_payment_attempt_status CHECK (status IN ('REQUESTED', 'SUCCEEDED', 'DECLINED', 'UNKNOWN')),
    CONSTRAINT ck_payment_attempt_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_attempt_version CHECK (version >= 0)
);

CREATE INDEX ix_payment_attempt_order ON payment_attempt (order_id);

CREATE TABLE payment_refund (
    operation_id UUID PRIMARY KEY,
    charge_operation_id UUID NOT NULL,
    order_id UUID NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    applied BOOLEAN NOT NULL,
    attempts INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_payment_refund_charge FOREIGN KEY (charge_operation_id) REFERENCES payment_attempt (operation_id),
    CONSTRAINT ck_payment_refund_status CHECK (status IN ('REQUESTED', 'REFUNDED', 'FAILED')),
    CONSTRAINT ck_payment_refund_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_refund_attempts CHECK (attempts >= 1),
    CONSTRAINT ck_payment_refund_applied CHECK (
        (status = 'REFUNDED' AND applied) OR (status <> 'REFUNDED' AND NOT applied)
    )
);

CREATE UNIQUE INDEX uq_payment_refund_applied_once
    ON payment_refund (charge_operation_id)
    WHERE applied;

CREATE TABLE simulator_due (
    id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    completed BOOLEAN NOT NULL,
    CONSTRAINT fk_simulator_due_attempt FOREIGN KEY (operation_id) REFERENCES payment_attempt (operation_id),
    CONSTRAINT ck_simulator_due_action CHECK (action IN ('COMPLETE_CHARGE'))
);

CREATE INDEX ix_simulator_due_pending ON simulator_due (due_at, id) WHERE NOT completed;

CREATE UNIQUE INDEX uq_simulator_due_open
    ON simulator_due (operation_id)
    WHERE NOT completed;

CREATE TABLE simulator_control (
    id SMALLINT PRIMARY KEY,
    charge_outcome VARCHAR(32) NOT NULL,
    refund_outcome VARCHAR(32) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_simulator_control_singleton CHECK (id = 1)
);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    topic VARCHAR(200) NOT NULL,
    message_key VARCHAR(200) NOT NULL,
    aggregate_id VARCHAR(200) NOT NULL,
    envelope TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SENT', 'DEAD')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_outbox_event_claim ON outbox_event (created_at, id)
    WHERE status IN ('PENDING', 'IN_PROGRESS');

CREATE TABLE inbox_event (
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE dead_letter (
    id UUID PRIMARY KEY,
    consumer_name VARCHAR(100) NOT NULL,
    event_id UUID,
    reason VARCHAR(200) NOT NULL,
    envelope TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
