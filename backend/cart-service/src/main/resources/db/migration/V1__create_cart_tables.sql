CREATE TABLE cart (
    id UUID PRIMARY KEY,
    owner_issuer VARCHAR(500) NOT NULL,
    owner_subject VARCHAR(255) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_cart_owner UNIQUE (owner_issuer, owner_subject),
    CONSTRAINT ck_cart_version_nonnegative CHECK (aggregate_version >= 0)
);

CREATE TABLE cart_item (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL,
    catalog_variant_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    image_url VARCHAR(2048),
    unit_price NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    snapshot_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES cart (id) ON DELETE CASCADE,
    CONSTRAINT uq_cart_item_variant UNIQUE (cart_id, catalog_variant_id),
    CONSTRAINT uq_cart_item_sku UNIQUE (cart_id, sku),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity >= 1 AND quantity <= 99),
    CONSTRAINT ck_cart_item_price_nonnegative CHECK (unit_price >= 0),
    CONSTRAINT ck_cart_item_sku_normalized CHECK (sku = upper(sku))
);

CREATE INDEX ix_cart_item_cart_id ON cart_item (cart_id);

CREATE FUNCTION enforce_cart_item_limit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF (SELECT COUNT(*) FROM cart_item WHERE cart_id = NEW.cart_id) >= 100 THEN
        RAISE EXCEPTION 'CART_ITEM_LIMIT';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER cart_item_limit
    BEFORE INSERT ON cart_item
    FOR EACH ROW
    EXECUTE FUNCTION enforce_cart_item_limit();
