--liquibase formatted sql

--changeset codex:008-daily-paper-valuations
CREATE TABLE paper_daily_valuations (
    comparison_id UUID NOT NULL REFERENCES strategy_comparisons(comparison_id),
    portfolio_id UUID NOT NULL REFERENCES paper_portfolios(portfolio_id),
    market_import_batch_id UUID NOT NULL REFERENCES market_import_batches(batch_id),
    session_date DATE NOT NULL,
    cash_usd NUMERIC(24, 8) NOT NULL CHECK (cash_usd >= 0),
    positions_usd NUMERIC(24, 8) NOT NULL CHECK (positions_usd >= 0),
    equity_usd NUMERIC(24, 8) NOT NULL CHECK (equity_usd >= 0),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comparison_id, portfolio_id, market_import_batch_id),
    CONSTRAINT ck_paper_daily_valuation_sum CHECK (equity_usd = cash_usd + positions_usd)
);

CREATE INDEX idx_paper_daily_valuations_session
    ON paper_daily_valuations(comparison_id, session_date DESC);

--rollback DROP TABLE paper_daily_valuations;
