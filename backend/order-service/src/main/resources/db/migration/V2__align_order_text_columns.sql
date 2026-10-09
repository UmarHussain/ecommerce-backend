ALTER TABLE customer_quote
    ALTER COLUMN currency TYPE VARCHAR(3),
    ALTER COLUMN address_country_code TYPE VARCHAR(2);

ALTER TABLE customer_order
    ALTER COLUMN currency TYPE VARCHAR(3);

ALTER TABLE order_address
    ALTER COLUMN country_code TYPE VARCHAR(2);

ALTER TABLE checkout_request
    ALTER COLUMN request_fingerprint TYPE VARCHAR(64);
