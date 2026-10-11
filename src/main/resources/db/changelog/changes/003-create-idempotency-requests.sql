--liquibase formatted sql

--changeset janjankowski:003-create-idempotency-requests
CREATE TABLE idempotency_requests (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    response_event_id UUID,
    response_proposal_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_idempotency_requests_fingerprint
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_idempotency_requests_response
        CHECK ((response_event_id IS NULL) = (response_proposal_id IS NULL))
);

--rollback DROP TABLE idempotency_requests;
