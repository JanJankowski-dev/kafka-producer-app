--liquibase formatted sql

--changeset codex:009-autonomous-paper
ALTER TABLE market_import_bars ADD COLUMN raw_open NUMERIC(24, 8);
ALTER TABLE market_import_bars ADD COLUMN split_factor NUMERIC(24, 8);
ALTER TABLE market_import_bars ADD COLUMN dividend_cash NUMERIC(24, 8);

CREATE TABLE paper_auto_decisions (
    decision_id UUID PRIMARY KEY,
    comparison_id UUID NOT NULL REFERENCES strategy_comparisons(comparison_id),
    portfolio_id UUID NOT NULL REFERENCES paper_portfolios(portfolio_id),
    market_import_batch_id UUID NOT NULL REFERENCES market_import_batches(batch_id),
    session_date DATE NOT NULL,
    instrument_id UUID NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    engine VARCHAR(16) NOT NULL CHECK (engine IN ('TRADITIONAL', 'AI')),
    engine_version VARCHAR(128) NOT NULL,
    action VARCHAR(8) NOT NULL CHECK (action IN ('BUY', 'SELL', 'HOLD')),
    reason VARCHAR(512) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (portfolio_id, session_date, instrument_id)
);

CREATE TABLE paper_auto_orders (
    order_id UUID PRIMARY KEY,
    decision_id UUID NOT NULL UNIQUE REFERENCES paper_auto_decisions(decision_id),
    portfolio_id UUID NOT NULL REFERENCES paper_portfolios(portfolio_id),
    instrument_id UUID NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    requested_session DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'FILLED', 'REJECTED')),
    quantity NUMERIC(24, 8),
    fill_price NUMERIC(24, 8),
    execution_session DATE,
    rejection_reason VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (portfolio_id, requested_session, instrument_id)
);

CREATE INDEX idx_paper_auto_orders_pending ON paper_auto_orders(portfolio_id, requested_session)
    WHERE status = 'PENDING';

--rollback DROP TABLE paper_auto_orders;
--rollback DROP TABLE paper_auto_decisions;
--rollback ALTER TABLE market_import_bars DROP COLUMN dividend_cash;
--rollback ALTER TABLE market_import_bars DROP COLUMN split_factor;
--rollback ALTER TABLE market_import_bars DROP COLUMN raw_open;
