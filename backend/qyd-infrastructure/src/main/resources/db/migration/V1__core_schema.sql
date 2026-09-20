CREATE TABLE qyd_user (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_qyd_user_username UNIQUE (username)
);

CREATE TABLE auth_refresh_token (
    id VARCHAR(36) PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES qyd_user(id)
);
CREATE INDEX idx_refresh_user_expiry ON auth_refresh_token(user_id, expires_at);

CREATE TABLE venue (
    id VARCHAR(36) PRIMARY KEY, name VARCHAR(200) NOT NULL, address VARCHAR(500),
    status VARCHAR(30) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_venue_status ON venue(status);

CREATE TABLE product (
    id VARCHAR(36) PRIMARY KEY, venue_id VARCHAR(36) NOT NULL, name VARCHAR(200) NOT NULL,
    price DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL, status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_venue FOREIGN KEY (venue_id) REFERENCES venue(id)
);
CREATE INDEX idx_product_venue_status ON product(venue_id, status);

CREATE TABLE qyd_order (
    id VARCHAR(36) PRIMARY KEY, order_no VARCHAR(64) NOT NULL, user_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL, amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_order_no UNIQUE(order_no),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES qyd_user(id),
    CONSTRAINT fk_order_product FOREIGN KEY (product_id) REFERENCES product(id)
);
CREATE INDEX idx_order_user_created ON qyd_order(user_id, created_at);
CREATE INDEX idx_order_status ON qyd_order(status);

CREATE TABLE payment (
    id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL, payment_no VARCHAR(64) NOT NULL,
    amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL, status VARCHAR(30) NOT NULL,
    paid_at TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_no UNIQUE(payment_no),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES qyd_order(id)
);
CREATE INDEX idx_payment_order ON payment(order_id);

CREATE TABLE marketing_campaign (
    id VARCHAR(36) PRIMARY KEY, name VARCHAR(200) NOT NULL, status VARCHAR(30) NOT NULL,
    starts_at TIMESTAMP(6), ends_at TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_campaign_status_time ON marketing_campaign(status, starts_at, ends_at);

CREATE TABLE content_article (
    id VARCHAR(36) PRIMARY KEY, title VARCHAR(300) NOT NULL, body TEXT NOT NULL,
    status VARCHAR(30) NOT NULL, published_at TIMESTAMP(6), created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_article_status_published ON content_article(status, published_at);

CREATE TABLE settlement_statement (
    id VARCHAR(36) PRIMARY KEY, merchant_id VARCHAR(36) NOT NULL, period_start TIMESTAMP(6) NOT NULL,
    period_end TIMESTAMP(6) NOT NULL, amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_settlement_merchant_period ON settlement_statement(merchant_id, period_start, period_end);

CREATE TABLE risk_event (
    id VARCHAR(36) PRIMARY KEY, subject_type VARCHAR(50) NOT NULL, subject_id VARCHAR(36) NOT NULL,
    risk_type VARCHAR(50) NOT NULL, level VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL,
    detail TEXT, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_risk_subject ON risk_event(subject_type, subject_id);
CREATE INDEX idx_risk_status_level ON risk_event(status, level);
