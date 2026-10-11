--liquibase formatted sql

--changeset janjankowski:002-create-outbox-events
CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    partition_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    schema_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claim_id UUID,
    claimed_until TIMESTAMP WITH TIME ZONE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(2000),
    CONSTRAINT chk_outbox_events_aggregate_type
        CHECK (LENGTH(BTRIM(aggregate_type)) > 0),
    CONSTRAINT chk_outbox_events_partition_key
        CHECK (LENGTH(BTRIM(partition_key)) > 0),
    CONSTRAINT chk_outbox_events_event_type
        CHECK (LENGTH(BTRIM(event_type)) > 0),
    CONSTRAINT chk_outbox_events_schema_version
        CHECK (schema_version > 0),
    CONSTRAINT chk_outbox_events_payload
        CHECK (JSONB_TYPEOF(payload) = 'object'),
    CONSTRAINT chk_outbox_events_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT chk_outbox_events_attempts
        CHECK (attempts >= 0),
    CONSTRAINT chk_outbox_events_claim
        CHECK (
            (claim_id IS NULL AND claimed_until IS NULL)
            OR (claim_id IS NOT NULL AND claimed_until IS NOT NULL)
        ),
    CONSTRAINT chk_outbox_events_terminal_claim
        CHECK (
            status = 'PENDING'
            OR (claim_id IS NULL AND claimed_until IS NULL)
        ),
    CONSTRAINT chk_outbox_events_published_at
        CHECK (
            (status = 'PUBLISHED' AND published_at IS NOT NULL)
            OR (status IN ('PENDING', 'DEAD') AND published_at IS NULL)
        )
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (next_attempt_at, claimed_until, occurred_at, event_id)
    WHERE status = 'PENDING';

--rollback DROP TABLE outbox_events;
