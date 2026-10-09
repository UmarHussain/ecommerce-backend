CREATE TABLE reservation (
    id UUID NOT NULL,
    order_id UUID NOT NULL,
    state VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ,
    payload_hash VARCHAR(64) NOT NULL,
    reserve_command_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_reservation PRIMARY KEY (id),
    CONSTRAINT uq_reservation_order UNIQUE (order_id),
    CONSTRAINT ck_reservation_state CHECK (state IN (
        'ACTIVE', 'CHECKOUT_HELD', 'CONSUMED', 'RELEASED', 'RESTOCKED'
    )),
    CONSTRAINT ck_reservation_active_expiry CHECK (state <> 'ACTIVE' OR expires_at IS NOT NULL),
    CONSTRAINT ck_reservation_hash CHECK (length(payload_hash) = 64),
    CONSTRAINT ck_reservation_version CHECK (version >= 0),
    CONSTRAINT ck_reservation_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX idx_reservation_active_expiry
    ON reservation (expires_at, id)
    WHERE state = 'ACTIVE';

CREATE TABLE reservation_line (
    id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    catalog_variant_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT pk_reservation_line PRIMARY KEY (id),
    CONSTRAINT fk_reservation_line_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE RESTRICT,
    CONSTRAINT uq_reservation_line_variant UNIQUE (reservation_id, catalog_variant_id),
    CONSTRAINT ck_reservation_line_sku CHECK (length(btrim(sku)) > 0),
    CONSTRAINT ck_reservation_line_quantity CHECK (quantity BETWEEN 1 AND 99)
);

CREATE TABLE reservation_command (
    command_id UUID NOT NULL,
    order_id UUID NOT NULL,
    command_type VARCHAR(32) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    result_event_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_reservation_command PRIMARY KEY (command_id),
    CONSTRAINT ck_reservation_command_type CHECK (command_type IN (
        'RESERVE', 'HOLD', 'RELEASE', 'CONSUME', 'RESTOCK'
    )),
    CONSTRAINT ck_reservation_command_hash CHECK (length(payload_hash) = 64),
    CONSTRAINT ck_reservation_command_result CHECK (result_event_type IN (
        'StockReserved', 'StockRejected',
        'ReservationHeld', 'HoldRejected',
        'StockReleased', 'ReleaseRejected',
        'StockConsumed', 'ConsumeRejected',
        'StockRestocked', 'RestockRejected'
    ))
);

CREATE TABLE reservation_tombstone (
    order_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_reservation_tombstone PRIMARY KEY (order_id)
);

CREATE TABLE reservation_history (
    id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    command_id UUID NOT NULL,
    from_state VARCHAR(32),
    to_state VARCHAR(32) NOT NULL,
    detail VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_reservation_history PRIMARY KEY (id),
    CONSTRAINT fk_reservation_history_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reservation_history_to_state CHECK (to_state IN (
        'ACTIVE', 'CHECKOUT_HELD', 'CONSUMED', 'RELEASED', 'RESTOCKED'
    )),
    CONSTRAINT ck_reservation_history_from_state CHECK (
        from_state IS NULL OR from_state IN (
            'ACTIVE', 'CHECKOUT_HELD', 'CONSUMED', 'RELEASED', 'RESTOCKED'
        )
    )
);

CREATE INDEX idx_reservation_history_reservation
    ON reservation_history (reservation_id, created_at, id);

CREATE TABLE stock_movement (
    id UUID NOT NULL,
    stock_item_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    command_id UUID NOT NULL,
    movement_type VARCHAR(16) NOT NULL,
    quantity INTEGER NOT NULL,
    before_on_hand INTEGER NOT NULL,
    after_on_hand INTEGER NOT NULL,
    before_reserved INTEGER NOT NULL,
    after_reserved INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stock_movement PRIMARY KEY (id),
    CONSTRAINT fk_stock_movement_item
        FOREIGN KEY (stock_item_id) REFERENCES stock_item (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movement_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservation (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movement_command
        FOREIGN KEY (command_id) REFERENCES reservation_command (command_id) ON DELETE RESTRICT,
    CONSTRAINT uq_stock_movement_command_item UNIQUE (command_id, stock_item_id),
    CONSTRAINT ck_stock_movement_type CHECK (movement_type IN ('CONSUME', 'RESTOCK')),
    CONSTRAINT ck_stock_movement_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_movement_bounds CHECK (
        before_on_hand >= 0 AND after_on_hand >= 0
        AND before_reserved >= 0 AND after_reserved >= 0
        AND before_reserved <= before_on_hand
        AND after_reserved <= after_on_hand
    ),
    CONSTRAINT ck_stock_movement_effect CHECK (
        (
            movement_type = 'CONSUME'
            AND after_on_hand = before_on_hand - quantity
            AND after_reserved = before_reserved - quantity
        )
        OR (
            movement_type = 'RESTOCK'
            AND after_on_hand = before_on_hand + quantity
            AND after_reserved = before_reserved
        )
    )
);

CREATE INDEX idx_stock_movement_item_created
    ON stock_movement (stock_item_id, created_at, id);

CREATE OR REPLACE FUNCTION inventory.reject_stock_movement_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'stock_movement is immutable';
END;
$$;

CREATE TRIGGER stock_movement_immutable
    BEFORE UPDATE OR DELETE ON stock_movement
    FOR EACH ROW
    EXECUTE FUNCTION inventory.reject_stock_movement_mutation();

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
    CONSTRAINT ck_outbox_event_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_event_version CHECK (event_version > 0)
);

CREATE INDEX idx_outbox_due ON outbox_event (status, next_attempt_at, created_at, id);
