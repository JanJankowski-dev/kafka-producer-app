--liquibase formatted sql

--changeset codex:005-paper-session-workflow
ALTER TABLE paper_portfolios ADD COLUMN last_session DATE;
ALTER TABLE paper_positions ADD COLUMN opened_session DATE;
ALTER TABLE paper_trades ADD COLUMN execution_session DATE;

ALTER TABLE screening_runs ADD COLUMN portfolio_id UUID REFERENCES paper_portfolios(portfolio_id);
ALTER TABLE screening_runs ADD COLUMN position_state_hash VARCHAR(64);
ALTER TABLE screening_runs DROP CONSTRAINT IF EXISTS screening_runs_snapshot_id_strategy_version_key;
CREATE UNIQUE INDEX uq_screening_runs_global
    ON screening_runs(snapshot_id, strategy_version) WHERE portfolio_id IS NULL;
CREATE UNIQUE INDEX uq_screening_runs_portfolio
    ON screening_runs(portfolio_id, snapshot_id, strategy_version) WHERE portfolio_id IS NOT NULL;

CREATE TABLE paper_orders (
    order_id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES paper_portfolios(portfolio_id),
    client_order_id UUID NOT NULL,
    run_id UUID NOT NULL,
    instrument_id UUID NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity NUMERIC(24, 8) NOT NULL CHECK (quantity > 0),
    requested_session DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'FILLED', 'REJECTED')),
    fill_trade_id UUID REFERENCES paper_trades(trade_id),
    rejection_reason VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (portfolio_id, client_order_id),
    FOREIGN KEY (run_id, instrument_id) REFERENCES screening_signals(run_id, instrument_id)
);
CREATE UNIQUE INDEX uq_paper_pending_instrument
    ON paper_orders(portfolio_id, instrument_id) WHERE status = 'PENDING';

--rollback DROP TABLE paper_orders;
--rollback DROP INDEX uq_screening_runs_portfolio;
--rollback DROP INDEX uq_screening_runs_global;
--rollback ALTER TABLE screening_runs DROP COLUMN portfolio_id;
--rollback ALTER TABLE screening_runs DROP COLUMN position_state_hash;
--rollback ALTER TABLE screening_runs ADD CONSTRAINT screening_runs_snapshot_id_strategy_version_key UNIQUE (snapshot_id, strategy_version);
--rollback ALTER TABLE paper_trades DROP COLUMN execution_session;
--rollback ALTER TABLE paper_positions DROP COLUMN opened_session;
--rollback ALTER TABLE paper_portfolios DROP COLUMN last_session;
