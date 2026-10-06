CREATE TABLE category (
    id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_category PRIMARY KEY (id),
    CONSTRAINT uq_category_slug UNIQUE (slug),
    CONSTRAINT ck_category_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_category_slug_shape
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_category_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_category_version CHECK (version >= 0)
);

CREATE TABLE product (
    id UUID NOT NULL,
    category_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_product PRIMARY KEY (id),
    CONSTRAINT uq_product_slug UNIQUE (slug),
    CONSTRAINT fk_product_category
        FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_product_slug_shape
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_product_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_product_version CHECK (version >= 0)
);

CREATE TABLE product_variant (
    id UUID NOT NULL,
    product_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    price NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    image_url VARCHAR(2048),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_product_variant PRIMARY KEY (id),
    CONSTRAINT uq_product_variant_sku UNIQUE (sku),
    CONSTRAINT fk_product_variant_product
        FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE RESTRICT,
    CONSTRAINT ck_product_variant_sku_normalized CHECK (sku = upper(sku)),
    CONSTRAINT ck_product_variant_sku_shape
        CHECK (sku ~ '^[A-Z0-9][A-Z0-9._-]{2,63}$'),
    CONSTRAINT ck_product_variant_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_product_variant_price CHECK (price >= 0),
    CONSTRAINT ck_product_variant_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_product_variant_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT ck_product_variant_version CHECK (version >= 0)
);

CREATE INDEX idx_category_active_lower_name
    ON category (lower(name))
    WHERE active;

CREATE INDEX idx_product_active_category
    ON product (category_id)
    WHERE active;

CREATE INDEX idx_product_active_lower_name
    ON product (lower(name))
    WHERE active;

CREATE INDEX idx_product_variant_active_product
    ON product_variant (product_id)
    WHERE active;

CREATE INDEX idx_product_variant_active_lower_name
    ON product_variant (lower(name))
    WHERE active;

CREATE INDEX idx_product_variant_active_price
    ON product_variant (price)
    WHERE active;

CREATE UNIQUE INDEX uq_product_variant_sku_normalized
    ON product_variant (upper(sku));
