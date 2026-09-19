--liquibase formatted sql

--changeset janjankowski:001-create-order-proposals
CREATE TABLE order_proposals (
    proposal_id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL,
    instrument_id UUID NOT NULL,
    side VARCHAR(4) NOT NULL,
    max_notional NUMERIC(38, 18) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    source VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROPOSED',
    rationale VARCHAR(2000) NOT NULL,
    confidence NUMERIC(19, 18) NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    proposed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_order_proposals_side
        CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_order_proposals_max_notional
        CHECK (max_notional > 0),
    CONSTRAINT chk_order_proposals_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_order_proposals_source
        CHECK (source IN ('BENCHMARK', 'SYSTEMATIC', 'AI_LAB')),
    CONSTRAINT chk_order_proposals_status
        CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT chk_order_proposals_rationale
        CHECK (LENGTH(BTRIM(rationale)) > 0),
    CONSTRAINT chk_order_proposals_confidence
        CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT chk_order_proposals_validity
        CHECK (valid_until > proposed_at)
);

CREATE TABLE order_proposal_evidence_sources (
    proposal_id UUID NOT NULL,
    source_position SMALLINT NOT NULL,
    source_uri VARCHAR(2048) NOT NULL,
    CONSTRAINT pk_order_proposal_evidence_sources
        PRIMARY KEY (proposal_id, source_position),
    CONSTRAINT fk_order_proposal_evidence_sources_proposal
        FOREIGN KEY (proposal_id)
        REFERENCES order_proposals (proposal_id)
        ON DELETE CASCADE,
    CONSTRAINT chk_order_proposal_evidence_sources_position
        CHECK (source_position >= 0),
    CONSTRAINT chk_order_proposal_evidence_sources_uri
        CHECK (LENGTH(BTRIM(source_uri)) > 0)
);

CREATE INDEX idx_order_proposals_portfolio_proposed_at
    ON order_proposals (portfolio_id, proposed_at DESC);

CREATE INDEX idx_order_proposals_instrument_proposed_at
    ON order_proposals (instrument_id, proposed_at DESC);

--rollback DROP TABLE order_proposal_evidence_sources;
--rollback DROP TABLE order_proposals;
