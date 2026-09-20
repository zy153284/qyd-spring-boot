CREATE TABLE outbox_event (
 id VARCHAR(36) PRIMARY KEY,
 aggregate_type VARCHAR(80) NOT NULL,
 aggregate_id VARCHAR(36) NOT NULL,
 event_type VARCHAR(120) NOT NULL,
 payload TEXT NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL,
 published_at TIMESTAMP(6),
 attempts INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_unpublished ON outbox_event(published_at,occurred_at);

CREATE TABLE consumed_event (
 id VARCHAR(36) PRIMARY KEY,
 consumer VARCHAR(100) NOT NULL,
 event_id VARCHAR(36) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uk_consumed_event UNIQUE(consumer,event_id)
);
