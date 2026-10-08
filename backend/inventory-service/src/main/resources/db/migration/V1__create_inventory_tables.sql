CREATE TABLE stock_item (
    id UUID NOT NULL,
    catalog_variant_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    on_hand INTEGER NOT NULL,
    reserved INTEGER NOT NULL,
    product_name_snapshot VARCHAR(200),
    variant_name_snapshot VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_item PRIMARY KEY (id),
    CONSTRAINT uq_stock_item_catalog_variant UNIQUE (catalog_variant_id),
    CONSTRAINT uq_stock_item_sku UNIQUE (sku),
    CONSTRAINT ck_stock_item_on_hand_nonnegative CHECK (on_hand >= 0),
    CONSTRAINT ck_stock_item_reserved_nonnegative CHECK (reserved >= 0),
    CONSTRAINT ck_stock_item_reserved_within_on_hand CHECK (reserved <= on_hand),
    CONSTRAINT ck_stock_item_sku_not_blank CHECK (length(btrim(sku)) > 0),
    CONSTRAINT ck_stock_item_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_stock_item_version CHECK (version >= 0)
);

CREATE TABLE stock_adjustment (
    id UUID NOT NULL,
    stock_item_id UUID NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    delta INTEGER NOT NULL,
    before_on_hand INTEGER NOT NULL,
    after_on_hand INTEGER NOT NULL,
    reserved_snapshot INTEGER NOT NULL,
    resulting_version BIGINT NOT NULL,
    reason_code VARCHAR(40) NOT NULL,
    note VARCHAR(500),
    reference_text VARCHAR(120),
    actor_issuer VARCHAR(300) NOT NULL,
    actor_subject VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stock_adjustment PRIMARY KEY (id),
    CONSTRAINT fk_stock_adjustment_item
        FOREIGN KEY (stock_item_id) REFERENCES stock_item (id) ON DELETE RESTRICT,
    CONSTRAINT ck_stock_adjustment_operation
        CHECK (operation_type IN ('SETUP', 'ADJUSTMENT')),
    CONSTRAINT ck_stock_adjustment_reason
        CHECK (reason_code IN ('OPENING_BALANCE', 'INBOUND_RECEIPT', 'CORRECTION', 'DAMAGE_LOSS', 'RETURN')),
    CONSTRAINT ck_stock_adjustment_quantities
        CHECK (before_on_hand >= 0 AND after_on_hand >= 0 AND reserved_snapshot >= 0),
    CONSTRAINT ck_stock_adjustment_reserved_within
        CHECK (reserved_snapshot <= before_on_hand AND reserved_snapshot <= after_on_hand),
    CONSTRAINT ck_stock_adjustment_delta
        CHECK (after_on_hand = before_on_hand + delta),
    CONSTRAINT ck_stock_adjustment_setup_sign
        CHECK (
            (operation_type = 'SETUP' AND reason_code = 'OPENING_BALANCE' AND delta >= 0 AND before_on_hand = 0)
            OR (operation_type = 'ADJUSTMENT' AND reason_code <> 'OPENING_BALANCE' AND delta <> 0)
        ),
    CONSTRAINT ck_stock_adjustment_resulting_version CHECK (resulting_version >= 0)
);

CREATE INDEX idx_stock_adjustment_item_created
    ON stock_adjustment (stock_item_id, created_at DESC, id DESC);

CREATE TABLE inventory_command (
    id UUID NOT NULL,
    actor_issuer VARCHAR(300) NOT NULL,
    actor_subject VARCHAR(200) NOT NULL,
    operation_scope VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    command_status VARCHAR(20) NOT NULL,
    http_status INTEGER,
    content_type VARCHAR(80),
    response_body TEXT,
    location VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT pk_inventory_command PRIMARY KEY (id),
    CONSTRAINT uq_inventory_command_scope
        UNIQUE (actor_issuer, actor_subject, operation_scope, idempotency_key),
    CONSTRAINT ck_inventory_command_scope
        CHECK (operation_scope IN ('SETUP', 'ADJUSTMENT')),
    CONSTRAINT ck_inventory_command_status
        CHECK (
            (command_status = 'IN_PROGRESS' AND http_status IS NULL AND response_body IS NULL AND completed_at IS NULL)
            OR (command_status = 'COMPLETED' AND http_status IS NOT NULL AND response_body IS NOT NULL AND completed_at IS NOT NULL)
        ),
    CONSTRAINT ck_inventory_command_key CHECK (length(btrim(idempotency_key)) > 0)
);

CREATE OR REPLACE FUNCTION inventory.reject_stock_adjustment_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'stock_adjustment is immutable';
END;
$$;

CREATE TRIGGER stock_adjustment_immutable
    BEFORE UPDATE OR DELETE ON stock_adjustment
    FOR EACH ROW
    EXECUTE FUNCTION inventory.reject_stock_adjustment_mutation();
