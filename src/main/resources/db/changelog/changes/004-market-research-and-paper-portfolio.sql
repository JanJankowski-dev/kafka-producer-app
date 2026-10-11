--liquibase formatted sql

--changeset codex:004-market-research-and-paper-portfolio
CREATE TABLE market_snapshots (
    snapshot_id VARCHAR(128) PRIMARY KEY,
    as_of_session DATE NOT NULL,
    synthetic BOOLEAN NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE market_instruments (
    snapshot_id VARCHAR(128) NOT NULL REFERENCES market_snapshots(snapshot_id),
    instrument_id UUID NOT NULL,
    market VARCHAR(8) NOT NULL,
    symbol VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    asset_type VARCHAR(16) NOT NULL,
    held_sessions INTEGER CHECK (held_sessions >= 0),
    PRIMARY KEY (snapshot_id, instrument_id)
);

CREATE TABLE market_daily_closes (
    snapshot_id VARCHAR(128) NOT NULL,
    instrument_id UUID NOT NULL,
    session_date DATE NOT NULL,
    adjusted_close NUMERIC(24, 8) NOT NULL CHECK (adjusted_close > 0),
    PRIMARY KEY (snapshot_id, instrument_id, session_date),
    FOREIGN KEY (snapshot_id, instrument_id)
        REFERENCES market_instruments(snapshot_id, instrument_id)
);

CREATE TABLE screening_runs (
    run_id UUID PRIMARY KEY,
    snapshot_id VARCHAR(128) NOT NULL REFERENCES market_snapshots(snapshot_id),
    strategy_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (snapshot_id, strategy_version)
);

CREATE TABLE screening_signals (
    run_id UUID NOT NULL REFERENCES screening_runs(run_id),
    instrument_id UUID NOT NULL,
    action VARCHAR(16) NOT NULL CHECK (action IN ('BUY', 'SELL', 'HOLD', 'NO_ACTION')),
    reason_code VARCHAR(80) NOT NULL,
    latest_adjusted_close NUMERIC(24, 8),
    return_over_20_sessions_pct NUMERIC(16, 4),
    max_holding_sessions INTEGER NOT NULL,
    PRIMARY KEY (run_id, instrument_id)
);

CREATE TABLE paper_portfolios (
    portfolio_id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE paper_cash (
    portfolio_id UUID NOT NULL REFERENCES paper_portfolios(portfolio_id),
    currency VARCHAR(3) NOT NULL,
    PRIMARY KEY (portfolio_id, currency),
    balance NUMERIC(24, 8) NOT NULL CHECK (balance >= 0)
);

CREATE TABLE paper_positions (
    portfolio_id UUID NOT NULL REFERENCES paper_portfolios(portfolio_id),
    instrument_id UUID NOT NULL,
    PRIMARY KEY (portfolio_id, instrument_id),
    symbol VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    quantity NUMERIC(24, 8) NOT NULL CHECK (quantity > 0),
    average_price NUMERIC(24, 8) NOT NULL CHECK (average_price > 0)
);

CREATE TABLE paper_trades (
    trade_id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES paper_portfolios(portfolio_id),
    client_order_id UUID NOT NULL,
    run_id UUID NOT NULL,
    instrument_id UUID NOT NULL,
    action VARCHAR(4) NOT NULL CHECK (action IN ('BUY', 'SELL')),
    quantity NUMERIC(24, 8) NOT NULL CHECK (quantity > 0),
    price NUMERIC(24, 8) NOT NULL CHECK (price > 0),
    currency VARCHAR(3) NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_paper_trade_signal FOREIGN KEY (run_id, instrument_id)
        REFERENCES screening_signals(run_id, instrument_id),
    CONSTRAINT uq_paper_trade_order UNIQUE (portfolio_id, client_order_id)
);

--rollback DROP TABLE paper_trades;
--rollback DROP TABLE paper_positions;
--rollback DROP TABLE paper_cash;
--rollback DROP TABLE paper_portfolios;
--rollback DROP TABLE screening_signals;
--rollback DROP TABLE screening_runs;
--rollback DROP TABLE market_daily_closes;
--rollback DROP TABLE market_instruments;
--rollback DROP TABLE market_snapshots;
