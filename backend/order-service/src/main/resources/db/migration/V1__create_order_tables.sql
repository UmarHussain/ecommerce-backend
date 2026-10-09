CREATE TABLE customer_quote (
    id UUID NOT NULL,
    owner_issuer VARCHAR(500) NOT NULL,
    owner_subject VARCHAR(255) NOT NULL,
    cart_version BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    merchandise_total NUMERIC(12, 2) NOT NULL,
    shipping_total NUMERIC(12, 2) NOT NULL,
    tax_total NUMERIC(12, 2) NOT NULL,
    grand_total NUMERIC(12, 2) NOT NULL,
    address_id UUID NOT NULL,
    address_label VARCHAR(80),
    address_line1 VARCHAR(200) NOT NULL,
    address_line2 VARCHAR(200),
    address_city VARCHAR(120) NOT NULL,
    address_region VARCHAR(120),
    address_postal_code VARCHAR(32) NOT NULL,
    address_country_code CHAR(2) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer_quote PRIMARY KEY (id),
    CONSTRAINT uq_customer_quote_consumed UNIQUE (consumed_order_id),
    CONSTRAINT ck_customer_quote_totals CHECK (
        merchandise_total >= 0 AND shipping_total = 0 AND tax_total = 0
        AND grand_total = merchandise_total
    ),
    CONSTRAINT ck_customer_quote_cart_version CHECK (cart_version >= 0)
);

CREATE TABLE quote_line (
    id UUID NOT NULL,
    quote_id UUID NOT NULL,
    catalog_variant_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    line_total NUMERIC(12, 2) NOT NULL,
    CONSTRAINT pk_quote_line PRIMARY KEY (id),
    CONSTRAINT fk_quote_line_quote FOREIGN KEY (quote_id) REFERENCES customer_quote (id) ON DELETE RESTRICT,
    CONSTRAINT uq_quote_line_sku UNIQUE (quote_id, sku),
    CONSTRAINT ck_quote_line_qty CHECK (quantity >= 1 AND quantity <= 99),
    CONSTRAINT ck_quote_line_money CHECK (unit_price >= 0 AND line_total = unit_price * quantity)
);

CREATE INDEX idx_quote_owner_created ON customer_quote (owner_issuer, owner_subject, created_at DESC);

CREATE TABLE customer_order (
    id UUID NOT NULL,
    owner_issuer VARCHAR(500) NOT NULL,
    owner_subject VARCHAR(255) NOT NULL,
    quote_id UUID NOT NULL,
    cart_version BIGINT NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    fulfilment_status VARCHAR(32) NOT NULL,
    saga_step VARCHAR(32) NOT NULL,
    terminal_plan VARCHAR(16) NOT NULL,
    cancellation_requested BOOLEAN NOT NULL,
    stock_consumed BOOLEAN NOT NULL,
    cleanup_status VARCHAR(32) NOT NULL,
    obligation VARCHAR(300) NOT NULL,
    currency CHAR(3) NOT NULL,
    merchandise_total NUMERIC(12, 2) NOT NULL,
    shipping_total NUMERIC(12, 2) NOT NULL,
    tax_total NUMERIC(12, 2) NOT NULL,
    grand_total NUMERIC(12, 2) NOT NULL,
    attempts INTEGER NOT NULL,
    reserve_command_id UUID NOT NULL,
    hold_command_id UUID,
    payment_command_id UUID,
    consume_command_id UUID,
    release_command_id UUID,
    refund_command_id UUID,
    restock_command_id UUID,
    cleanup_command_id UUID,
    deadline_at TIMESTAMPTZ,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer_order PRIMARY KEY (id),
    CONSTRAINT uq_customer_order_quote UNIQUE (quote_id),
    CONSTRAINT fk_customer_order_quote FOREIGN KEY (quote_id) REFERENCES customer_quote (id) ON DELETE RESTRICT,
    CONSTRAINT ck_customer_order_status CHECK (order_status IN (
        'PENDING_STOCK', 'PENDING_HOLD', 'PENDING_PAYMENT', 'PENDING_CONSUMPTION', 'CONFIRMED',
        'COMPENSATING', 'CANCEL_PENDING', 'REJECTED', 'CANCELLED', 'MANUAL_REVIEW'
    )),
    CONSTRAINT ck_customer_order_payment CHECK (payment_status IN (
        'NOT_STARTED', 'REQUESTED', 'SUCCEEDED', 'DECLINED', 'UNKNOWN',
        'REFUND_REQUESTED', 'REFUNDED', 'REFUND_FAILED'
    )),
    CONSTRAINT ck_customer_order_fulfilment CHECK (fulfilment_status = 'NOT_STARTED'),
    CONSTRAINT ck_customer_order_step CHECK (saga_step IN (
        'AWAIT_RESERVATION', 'AWAIT_HOLD', 'AWAIT_PAYMENT', 'AWAIT_RECONCILE', 'AWAIT_CONSUMPTION',
        'AWAIT_CLEANUP', 'AWAIT_RELEASE', 'AWAIT_REFUND', 'AWAIT_RESTOCK', 'COMPLETED', 'MANUAL_REVIEW'
    )),
    CONSTRAINT ck_customer_order_totals CHECK (
        merchandise_total >= 0 AND shipping_total = 0 AND tax_total = 0 AND grand_total = merchandise_total
    ),
    CONSTRAINT ck_customer_order_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_customer_order_owner_created
    ON customer_order (owner_issuer, owner_subject, created_at DESC, id DESC);

CREATE TABLE order_line (
    id UUID NOT NULL,
    order_id UUID NOT NULL,
    catalog_variant_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    line_total NUMERIC(12, 2) NOT NULL,
    CONSTRAINT pk_order_line PRIMARY KEY (id),
    CONSTRAINT fk_order_line_order FOREIGN KEY (order_id) REFERENCES customer_order (id) ON DELETE RESTRICT,
    CONSTRAINT uq_order_line_sku UNIQUE (order_id, sku),
    CONSTRAINT ck_order_line_qty CHECK (quantity >= 1 AND quantity <= 99)
);

CREATE TABLE order_address (
    order_id UUID NOT NULL,
    address_id UUID NOT NULL,
    label VARCHAR(80),
    line1 VARCHAR(200) NOT NULL,
    line2 VARCHAR(200),
    city VARCHAR(120) NOT NULL,
    region VARCHAR(120),
    postal_code VARCHAR(32) NOT NULL,
    country_code CHAR(2) NOT NULL,
    CONSTRAINT pk_order_address PRIMARY KEY (order_id),
    CONSTRAINT fk_order_address_order FOREIGN KEY (order_id) REFERENCES customer_order (id) ON DELETE RESTRICT
);

CREATE TABLE order_history (
    id UUID NOT NULL,
    order_id UUID NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    fulfilment_status VARCHAR(32) NOT NULL,
    saga_step VARCHAR(32) NOT NULL,
    detail VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_order_history PRIMARY KEY (id),
    CONSTRAINT fk_order_history_order FOREIGN KEY (order_id) REFERENCES customer_order (id) ON DELETE RESTRICT
);

CREATE INDEX idx_order_history_order ON order_history (order_id, created_at, id);

CREATE TABLE checkout_request (
    id UUID NOT NULL,
    owner_issuer VARCHAR(500) NOT NULL,
    owner_subject VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    request_status VARCHAR(20) NOT NULL,
    http_status INTEGER,
    response_body TEXT,
    order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT pk_checkout_request PRIMARY KEY (id),
    CONSTRAINT uq_checkout_request_owner_key UNIQUE (owner_issuer, owner_subject, idempotency_key),
    CONSTRAINT ck_checkout_request_status CHECK (
        (request_status = 'IN_PROGRESS' AND http_status IS NULL AND response_body IS NULL AND completed_at IS NULL)
        OR (request_status = 'COMPLETED' AND http_status IS NOT NULL AND response_body IS NOT NULL AND completed_at IS NOT NULL)
    )
);

CREATE TABLE outbox_event (
    id UUID NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL,
    saga_id UUID,
    command_id UUID,
    correlation_id VARCHAR(80) NOT NULL,
    causation_id UUID,
    topic VARCHAR(120) NOT NULL,
    message_key VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    envelope TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL,
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_outbox_event_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SENT', 'DEAD')),
    CONSTRAINT ck_outbox_event_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_due ON outbox_event (status, next_attempt_at, created_at, id);

CREATE TABLE inbox_event (
    consumer_name VARCHAR(80) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_inbox_event PRIMARY KEY (consumer_name, event_id)
);

CREATE TABLE dead_letter (
    id UUID NOT NULL,
    consumer_name VARCHAR(80) NOT NULL,
    event_id UUID,
    topic VARCHAR(120) NOT NULL,
    message_key VARCHAR(80),
    payload TEXT NOT NULL,
    reason VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_dead_letter PRIMARY KEY (id)
);
