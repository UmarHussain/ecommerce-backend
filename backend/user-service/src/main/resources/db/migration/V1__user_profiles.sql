CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    issuer VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    email_snapshot VARCHAR(320),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_app_user_issuer_subject UNIQUE (issuer, subject)
);

CREATE TABLE customer_profile (
    user_id UUID PRIMARY KEY REFERENCES app_user (id),
    preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE customer_address (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user (id),
    label VARCHAR(80) NOT NULL,
    line1 VARCHAR(255) NOT NULL,
    line2 VARCHAR(255),
    city VARCHAR(120) NOT NULL,
    region VARCHAR(120),
    postal_code VARCHAR(32) NOT NULL,
    country_code CHAR(2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL
);

CREATE INDEX idx_customer_address_user ON customer_address (user_id);

CREATE TABLE staff_profile (
    user_id UUID PRIMARY KEY REFERENCES app_user (id),
    employee_reference VARCHAR(80),
    department VARCHAR(120),
    onboarding_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE identity_operation (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    target_identity VARCHAR(255),
    operation_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    result_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_identity_operation_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_identity_operation_status ON identity_operation (status, next_attempt_at);

CREATE TABLE access_audit (
    id UUID PRIMARY KEY,
    actor_issuer VARCHAR(255) NOT NULL,
    actor_subject VARCHAR(255) NOT NULL,
    target_user_id UUID,
    action VARCHAR(64) NOT NULL,
    before_state JSONB,
    after_state JSONB,
    outcome VARCHAR(32) NOT NULL,
    correlation_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_access_audit_target ON access_audit (target_user_id, created_at DESC);
CREATE INDEX idx_access_audit_actor ON access_audit (actor_subject, created_at DESC);

CREATE TABLE role_bundle (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500),
    permissions JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_role_bundle_name UNIQUE (name)
);
