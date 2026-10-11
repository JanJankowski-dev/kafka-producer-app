--liquibase formatted sql

--changeset codex:011-etf-benchmark-market-closes
CREATE TABLE etf_benchmark_market_closes (
    fund_code VARCHAR(16) NOT NULL CHECK (fund_code IN ('CSPX', 'IWDA')),
    session_date DATE NOT NULL,
    close_usd NUMERIC(24, 8) NOT NULL CHECK (close_usd > 0),
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (fund_code, session_date)
);

--rollback DROP TABLE etf_benchmark_market_closes;
