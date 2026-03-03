-- Create outbox_events table for Transactional Outbox pattern
CREATE TABLE IF NOT EXISTS outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(50) NOT NULL,
    kafka_topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_retry_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for efficient polling of unpublished events
CREATE INDEX idx_outbox_unpublished ON outbox_events(published, created_at) WHERE published = FALSE;

-- Index for cleanup of old published events
CREATE INDEX idx_outbox_published_at ON outbox_events(published_at) WHERE published = TRUE;
