--liquibase formatted sql

--changeset codex:006-real-market-imports
CREATE TABLE market_import_batches (
    batch_id UUID PRIMARY KEY,
    source VARCHAR(32) NOT NULL,
    universe_version VARCHAR(80) NOT NULL,
    universe_as_of DATE NOT NULL,
    latest_common_session DATE NOT NULL,
    content_hash VARCHAR(64) NOT NULL UNIQUE,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    instrument_count INTEGER NOT NULL CHECK (instrument_count = 10),
    session_count INTEGER NOT NULL CHECK (session_count >= 21)
);

CREATE TABLE market_import_bars (
    batch_id UUID NOT NULL REFERENCES market_import_batches(batch_id),
    instrument_id UUID NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    session_date DATE NOT NULL,
    raw_close NUMERIC(24, 8) NOT NULL CHECK (raw_close > 0),
    adjusted_close NUMERIC(24, 8) NOT NULL CHECK (adjusted_close > 0),
    volume BIGINT NOT NULL CHECK (volume >= 0),
    PRIMARY KEY (batch_id, instrument_id, session_date)
);

CREATE INDEX idx_market_import_bars_session
    ON market_import_bars(batch_id, session_date);

--rollback DROP TABLE market_import_bars;
--rollback DROP TABLE market_import_batches;
