CREATE TABLE sport_category (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(100) NOT NULL, description VARCHAR(500),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_sport_category_name ON sport_category(name);

CREATE TABLE sport_resource (
 id VARCHAR(36) PRIMARY KEY, venue_id VARCHAR(36) NOT NULL, category_id VARCHAR(36) NOT NULL,
 name VARCHAR(150) NOT NULL, description VARCHAR(500), active BOOLEAN NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_resource_venue FOREIGN KEY (venue_id) REFERENCES venue(id),
 CONSTRAINT fk_resource_category FOREIGN KEY (category_id) REFERENCES sport_category(id)
);
CREATE INDEX idx_resource_venue_category ON sport_resource(venue_id, category_id, active);

CREATE TABLE product_sku (
 id VARCHAR(36) PRIMARY KEY, resource_id VARCHAR(36) NOT NULL, name VARCHAR(150) NOT NULL,
 price DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL, active BOOLEAN NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_sku_price CHECK (price >= 0),
 CONSTRAINT fk_sku_resource FOREIGN KEY (resource_id) REFERENCES sport_resource(id)
);
CREATE INDEX idx_sku_resource_active ON product_sku(resource_id, active);

CREATE TABLE slot_inventory (
 id VARCHAR(36) PRIMARY KEY, sku_id VARCHAR(36) NOT NULL, starts_at TIMESTAMP(6) NOT NULL,
 ends_at TIMESTAMP(6) NOT NULL, capacity INT NOT NULL, reserved INT NOT NULL DEFAULT 0, sold INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_slot_sku_time UNIQUE(sku_id, starts_at, ends_at),
 CONSTRAINT ck_slot_time CHECK (ends_at > starts_at),
 CONSTRAINT ck_slot_counts CHECK (capacity > 0 AND reserved >= 0 AND sold >= 0 AND reserved + sold <= capacity),
 CONSTRAINT fk_slot_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id)
);
CREATE INDEX idx_slot_sku_start ON slot_inventory(sku_id, starts_at);

CREATE TABLE customer_order (
 id VARCHAR(36) PRIMARY KEY, order_no VARCHAR(32) NOT NULL, user_id VARCHAR(36) NOT NULL,
 idempotency_key VARCHAR(100) NOT NULL, amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL, status VARCHAR(30) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_customer_order_no UNIQUE(order_no), CONSTRAINT uk_order_user_idempotency UNIQUE(user_id,idempotency_key),
 CONSTRAINT ck_order_amount CHECK(amount >= 0), CONSTRAINT fk_customer_order_user FOREIGN KEY(user_id) REFERENCES qyd_user(id)
);
CREATE INDEX idx_customer_order_user_created ON customer_order(user_id,created_at);
CREATE INDEX idx_customer_order_status ON customer_order(status);

CREATE TABLE order_item (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL, sku_id VARCHAR(36) NOT NULL, slot_id VARCHAR(36) NOT NULL,
 sku_name VARCHAR(150) NOT NULL, quantity INT NOT NULL, unit_price DECIMAL(19,2) NOT NULL, amount DECIMAL(19,2) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_order_item_quantity CHECK(quantity > 0), CONSTRAINT fk_item_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CONSTRAINT fk_item_sku FOREIGN KEY(sku_id) REFERENCES product_sku(id), CONSTRAINT fk_item_slot FOREIGN KEY(slot_id) REFERENCES slot_inventory(id)
);
CREATE INDEX idx_item_order ON order_item(order_id);

CREATE TABLE inventory_reservation (
 id VARCHAR(36) PRIMARY KEY, slot_id VARCHAR(36) NOT NULL, order_id VARCHAR(36) NOT NULL, order_item_id VARCHAR(36) NOT NULL,
 quantity INT NOT NULL, status VARCHAR(20) NOT NULL, expires_at TIMESTAMP(6) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_reservation_item UNIQUE(order_item_id), CONSTRAINT ck_reservation_qty CHECK(quantity > 0),
 CONSTRAINT fk_reservation_slot FOREIGN KEY(slot_id) REFERENCES slot_inventory(id),
 CONSTRAINT fk_reservation_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CONSTRAINT fk_reservation_item FOREIGN KEY(order_item_id) REFERENCES order_item(id)
);
CREATE INDEX idx_reservation_order_status ON inventory_reservation(order_id,status);
CREATE INDEX idx_reservation_expiry ON inventory_reservation(status,expires_at);

CREATE TABLE order_status_history (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL, from_status VARCHAR(30), to_status VARCHAR(30) NOT NULL, reason VARCHAR(255),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_history_order FOREIGN KEY(order_id) REFERENCES customer_order(id)
);
CREATE INDEX idx_order_history ON order_status_history(order_id,created_at);

CREATE TABLE payment_order (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL, payment_no VARCHAR(32) NOT NULL, amount DECIMAL(19,2) NOT NULL,
 currency VARCHAR(3) NOT NULL, status VARCHAR(20) NOT NULL, checkout_token VARCHAR(100), provider_transaction_id VARCHAR(100), paid_at TIMESTAMP(6),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_payment_order_no UNIQUE(payment_no), CONSTRAINT uk_provider_transaction UNIQUE(provider_transaction_id),
 CONSTRAINT ck_payment_amount CHECK(amount > 0), CONSTRAINT fk_payment_customer_order FOREIGN KEY(order_id) REFERENCES customer_order(id)
);
CREATE INDEX idx_payment_order_status ON payment_order(order_id,status);

CREATE TABLE payment_callback (
 id VARCHAR(36) PRIMARY KEY, provider VARCHAR(30) NOT NULL, callback_id VARCHAR(100) NOT NULL, payment_id VARCHAR(36) NOT NULL,
 payload_status VARCHAR(30) NOT NULL, created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_callback_provider_id UNIQUE(provider,callback_id), CONSTRAINT fk_callback_payment FOREIGN KEY(payment_id) REFERENCES payment_order(id)
);

CREATE TABLE payment_refund (
 id VARCHAR(36) PRIMARY KEY, payment_id VARCHAR(36) NOT NULL, refund_no VARCHAR(32) NOT NULL, amount DECIMAL(19,2) NOT NULL,
 reason VARCHAR(255), status VARCHAR(20) NOT NULL, provider_refund_id VARCHAR(100),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_refund_no UNIQUE(refund_no), CONSTRAINT ck_refund_amount CHECK(amount > 0),
 CONSTRAINT fk_refund_payment FOREIGN KEY(payment_id) REFERENCES payment_order(id)
);
CREATE INDEX idx_refund_payment_status ON payment_refund(payment_id,status);

CREATE TABLE redemption_code (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL, code_hash VARCHAR(64) NOT NULL, status VARCHAR(20) NOT NULL, redeemed_at TIMESTAMP(6),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_redemption_hash UNIQUE(code_hash), CONSTRAINT fk_redemption_order FOREIGN KEY(order_id) REFERENCES customer_order(id)
);
CREATE INDEX idx_redemption_order_status ON redemption_code(order_id,status);
