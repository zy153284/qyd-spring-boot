CREATE TABLE coupon_definition (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(120) NOT NULL, description VARCHAR(500),
 discount_amount DECIMAL(19,2) NOT NULL, minimum_amount DECIMAL(19,2) NOT NULL,
 total_quantity INT NOT NULL, claimed_quantity INT NOT NULL DEFAULT 0,
 starts_at TIMESTAMP(6) NOT NULL, ends_at TIMESTAMP(6) NOT NULL, status VARCHAR(20) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_coupon_amount CHECK(discount_amount > 0 AND minimum_amount >= discount_amount),
 CONSTRAINT ck_coupon_quantity CHECK(total_quantity > 0 AND claimed_quantity >= 0 AND claimed_quantity <= total_quantity)
);
CREATE INDEX idx_coupon_status_time ON coupon_definition(status, starts_at, ends_at);

CREATE TABLE user_coupon (
 id VARCHAR(36) PRIMARY KEY, coupon_id VARCHAR(36) NOT NULL, user_id VARCHAR(36) NOT NULL,
 status VARCHAR(20) NOT NULL, claimed_at TIMESTAMP(6) NOT NULL, redeemed_at TIMESTAMP(6),
 order_id VARCHAR(36), created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_user_coupon UNIQUE(coupon_id, user_id),
 CONSTRAINT fk_user_coupon_definition FOREIGN KEY(coupon_id) REFERENCES coupon_definition(id),
 CONSTRAINT fk_user_coupon_user FOREIGN KEY(user_id) REFERENCES qyd_user(id),
 CONSTRAINT fk_user_coupon_order FOREIGN KEY(order_id) REFERENCES customer_order(id)
);
CREATE INDEX idx_user_coupon_user_status ON user_coupon(user_id, status);

CREATE TABLE member_account (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, level VARCHAR(30) NOT NULL,
 points_balance BIGINT NOT NULL DEFAULT 0, growth_value BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_member_user UNIQUE(user_id), CONSTRAINT ck_member_points CHECK(points_balance >= 0),
 CONSTRAINT fk_member_user FOREIGN KEY(user_id) REFERENCES qyd_user(id)
);

CREATE TABLE points_ledger (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, change_amount BIGINT NOT NULL,
 balance_after BIGINT NOT NULL, type VARCHAR(30) NOT NULL, reference_type VARCHAR(30),
 reference_id VARCHAR(36), remark VARCHAR(255), created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_points_reference UNIQUE(user_id, type, reference_type, reference_id),
 CONSTRAINT fk_points_user FOREIGN KEY(user_id) REFERENCES qyd_user(id)
);
CREATE INDEX idx_points_user_created ON points_ledger(user_id, created_at);

CREATE TABLE venue_favorite (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL, venue_id VARCHAR(36) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_favorite_user_venue UNIQUE(user_id, venue_id),
 CONSTRAINT fk_favorite_user FOREIGN KEY(user_id) REFERENCES qyd_user(id),
 CONSTRAINT fk_favorite_venue FOREIGN KEY(venue_id) REFERENCES venue(id)
);
CREATE INDEX idx_favorite_user_created ON venue_favorite(user_id, created_at);

CREATE TABLE order_review (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL, user_id VARCHAR(36) NOT NULL,
 venue_id VARCHAR(36) NOT NULL, rating INT NOT NULL, content VARCHAR(1000),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_review_order UNIQUE(order_id), CONSTRAINT ck_review_rating CHECK(rating BETWEEN 1 AND 5),
 CONSTRAINT fk_review_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CONSTRAINT fk_review_user FOREIGN KEY(user_id) REFERENCES qyd_user(id),
 CONSTRAINT fk_review_venue FOREIGN KEY(venue_id) REFERENCES venue(id)
);
CREATE INDEX idx_review_venue_created ON order_review(venue_id, created_at);

CREATE TABLE content_item (
 id VARCHAR(36) PRIMARY KEY, type VARCHAR(20) NOT NULL, title VARCHAR(300) NOT NULL,
 summary VARCHAR(500), body TEXT NOT NULL, image_url VARCHAR(500), target_url VARCHAR(500),
 status VARCHAR(20) NOT NULL, published_at TIMESTAMP(6), created_by VARCHAR(36) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_content_creator FOREIGN KEY(created_by) REFERENCES qyd_user(id)
);
CREATE INDEX idx_content_type_status_published ON content_item(type, status, published_at);

CREATE TABLE settlement_eligibility (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL, venue_id VARCHAR(36) NOT NULL,
 eligible_amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL, fulfilled_at TIMESTAMP(6) NOT NULL,
 statement_id VARCHAR(36), created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_eligibility_order_venue UNIQUE(order_id, venue_id),
 CONSTRAINT ck_eligibility_amount CHECK(eligible_amount > 0),
 CONSTRAINT fk_eligibility_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CONSTRAINT fk_eligibility_venue FOREIGN KEY(venue_id) REFERENCES venue(id)
);
CREATE INDEX idx_eligibility_venue_statement ON settlement_eligibility(venue_id, statement_id, fulfilled_at);

CREATE TABLE settlement_batch (
 id VARCHAR(36) PRIMARY KEY, statement_no VARCHAR(40) NOT NULL, venue_id VARCHAR(36) NOT NULL,
 period_start TIMESTAMP(6) NOT NULL, period_end TIMESTAMP(6) NOT NULL,
 gross_amount DECIMAL(19,2) NOT NULL, adjustment_amount DECIMAL(19,2) NOT NULL,
 net_amount DECIMAL(19,2) NOT NULL, currency VARCHAR(3) NOT NULL, status VARCHAR(20) NOT NULL,
 idempotency_key VARCHAR(100) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_settlement_no UNIQUE(statement_no), CONSTRAINT uk_settlement_idempotency UNIQUE(idempotency_key),
 CONSTRAINT fk_settlement_venue FOREIGN KEY(venue_id) REFERENCES venue(id)
);
CREATE INDEX idx_settlement_venue_period ON settlement_batch(venue_id, period_start, period_end);

ALTER TABLE settlement_eligibility ADD CONSTRAINT fk_eligibility_statement
 FOREIGN KEY(statement_id) REFERENCES settlement_batch(id);

CREATE TABLE settlement_adjustment (
 id VARCHAR(36) PRIMARY KEY, statement_id VARCHAR(36), order_id VARCHAR(36) NOT NULL,
 refund_id VARCHAR(36) NOT NULL, amount DECIMAL(19,2) NOT NULL, reason VARCHAR(255),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_adjustment_refund UNIQUE(refund_id), CONSTRAINT ck_adjustment_amount CHECK(amount < 0),
 CONSTRAINT fk_adjustment_statement FOREIGN KEY(statement_id) REFERENCES settlement_batch(id),
 CONSTRAINT fk_adjustment_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CONSTRAINT fk_adjustment_refund FOREIGN KEY(refund_id) REFERENCES payment_refund(id)
);
CREATE INDEX idx_adjustment_statement ON settlement_adjustment(statement_id);

CREATE TABLE risk_blacklist (
 id VARCHAR(36) PRIMARY KEY, subject_type VARCHAR(30) NOT NULL, subject_value VARCHAR(150) NOT NULL,
 reason VARCHAR(500) NOT NULL, active BOOLEAN NOT NULL, expires_at TIMESTAMP(6),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_blacklist_subject UNIQUE(subject_type, subject_value)
);
CREATE INDEX idx_blacklist_active_expiry ON risk_blacklist(active, expires_at);

CREATE TABLE risk_rule (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(120) NOT NULL, code VARCHAR(60) NOT NULL,
 expression VARCHAR(1000) NOT NULL, level VARCHAR(20) NOT NULL, enabled BOOLEAN NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_risk_rule_code UNIQUE(code)
);

CREATE TABLE order_risk_check (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL, user_id VARCHAR(36) NOT NULL,
 decision VARCHAR(20) NOT NULL, matched_rules VARCHAR(1000), detail VARCHAR(1000),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_risk_check_order FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CONSTRAINT fk_risk_check_user FOREIGN KEY(user_id) REFERENCES qyd_user(id)
);
CREATE INDEX idx_risk_check_order_created ON order_risk_check(order_id, created_at);

CREATE TABLE audit_log (
 id VARCHAR(36) PRIMARY KEY, actor_id VARCHAR(36) NOT NULL, action VARCHAR(80) NOT NULL,
 resource_type VARCHAR(60) NOT NULL, resource_id VARCHAR(36), detail VARCHAR(1000),
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_audit_actor_created ON audit_log(actor_id, created_at);
CREATE INDEX idx_audit_resource_created ON audit_log(resource_type, resource_id, created_at);
