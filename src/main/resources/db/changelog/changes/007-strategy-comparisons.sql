--liquibase formatted sql

--changeset codex:007-strategy-comparisons
CREATE TABLE strategy_comparisons (
    comparison_id UUID PRIMARY KEY,
    market_import_batch_id UUID NOT NULL UNIQUE REFERENCES market_import_batches(batch_id),
    traditional_portfolio_id UUID NOT NULL UNIQUE REFERENCES paper_portfolios(portfolio_id),
    ai_portfolio_id UUID NOT NULL UNIQUE REFERENCES paper_portfolios(portfolio_id),
    traditional_strategy_version VARCHAR(64) NOT NULL,
    ai_model_version VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_strategy_comparison_distinct_portfolios
        CHECK (traditional_portfolio_id <> ai_portfolio_id)
);

--rollback DROP TABLE strategy_comparisons;
