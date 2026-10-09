CREATE TABLE cleanup_effect (
    command_id UUID NOT NULL,
    order_id UUID NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cleanup_effect PRIMARY KEY (command_id),
    CONSTRAINT ck_cleanup_effect_outcome CHECK (outcome IN ('CLEARED', 'SKIPPED'))
);

CREATE TABLE inbox_event (
    consumer_name VARCHAR(80) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_inbox_event PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE outbox_event (
    id UUID NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    command_id UUID,
    topic VARCHAR(120) NOT NULL,
    message_key VARCHAR(80) NOT NULL,
    envelope TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SENT'))
);

CREATE TABLE dead_letter (
    id UUID NOT NULL,
    consumer_name VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    reason VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_dead_letter PRIMARY KEY (id)
);
