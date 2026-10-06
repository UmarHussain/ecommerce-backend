INSERT INTO category AS existing (id, name, slug, active)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'Electronics', 'electronics', TRUE),
    ('10000000-0000-0000-0000-000000000002', 'Books', 'books', TRUE)
ON CONFLICT (slug) DO UPDATE
SET name = EXCLUDED.name,
    active = EXCLUDED.active,
    updated_at = CURRENT_TIMESTAMP,
    version = existing.version + 1;

INSERT INTO product AS existing (id, category_id, name, slug, description, active)
SELECT seed.id,
       category_seed.id,
       seed.name,
       seed.slug,
       seed.description,
       TRUE
FROM (
    VALUES
        (
            '20000000-0000-0000-0000-000000000001'::UUID,
            'Wireless Headphones',
            'wireless-headphones',
            'Comfortable over-ear wireless headphones for everyday listening.',
            'electronics'
        ),
        (
            '20000000-0000-0000-0000-000000000002'::UUID,
            'Mechanical Keyboard',
            'mechanical-keyboard',
            'Compact mechanical keyboard with tactile switches.',
            'electronics'
        ),
        (
            '20000000-0000-0000-0000-000000000003'::UUID,
            'Cloud Architecture Handbook',
            'cloud-architecture-handbook',
            'A practical introduction to resilient cloud application design.',
            'books'
        )
) AS seed(id, name, slug, description, category_slug)
JOIN category AS category_seed ON category_seed.slug = seed.category_slug
ON CONFLICT (slug) DO UPDATE
SET category_id = EXCLUDED.category_id,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    active = EXCLUDED.active,
    updated_at = CURRENT_TIMESTAMP,
    version = existing.version + 1;

INSERT INTO product_variant AS existing (
    id,
    product_id,
    sku,
    name,
    price,
    currency,
    image_url,
    active
)
SELECT seed.id,
       product_seed.id,
       seed.sku,
       seed.name,
       seed.price,
       seed.currency,
       seed.image_url,
       TRUE
FROM (
    VALUES
        (
            '30000000-0000-0000-0000-000000000001'::UUID,
            'wireless-headphones',
            'HEADPHONES-BLK',
            'Wireless Headphones - Black',
            79.9900::NUMERIC(19, 4),
            'USD',
            'https://example.com/catalog/headphones-black.jpg'
        ),
        (
            '30000000-0000-0000-0000-000000000002'::UUID,
            'wireless-headphones',
            'HEADPHONES-WHT',
            'Wireless Headphones - White',
            79.9900::NUMERIC(19, 4),
            'USD',
            'https://example.com/catalog/headphones-white.jpg'
        ),
        (
            '30000000-0000-0000-0000-000000000003'::UUID,
            'mechanical-keyboard',
            'KEYBOARD-US',
            'Mechanical Keyboard - US Layout',
            109.0000::NUMERIC(19, 4),
            'USD',
            'https://example.com/catalog/keyboard-us.jpg'
        ),
        (
            '30000000-0000-0000-0000-000000000004'::UUID,
            'mechanical-keyboard',
            'KEYBOARD-UK',
            'Mechanical Keyboard - UK Layout',
            109.0000::NUMERIC(19, 4),
            'USD',
            'https://example.com/catalog/keyboard-uk.jpg'
        ),
        (
            '30000000-0000-0000-0000-000000000005'::UUID,
            'cloud-architecture-handbook',
            'BOOK-CLOUD-PAPER',
            'Cloud Architecture Handbook - Paperback',
            39.9500::NUMERIC(19, 4),
            'USD',
            'https://example.com/catalog/cloud-architecture-paperback.jpg'
        )
) AS seed(id, product_slug, sku, name, price, currency, image_url)
JOIN product AS product_seed ON product_seed.slug = seed.product_slug
ON CONFLICT (sku) DO UPDATE
SET product_id = EXCLUDED.product_id,
    name = EXCLUDED.name,
    price = EXCLUDED.price,
    currency = EXCLUDED.currency,
    image_url = EXCLUDED.image_url,
    active = EXCLUDED.active,
    updated_at = CURRENT_TIMESTAMP,
    version = existing.version + 1;
