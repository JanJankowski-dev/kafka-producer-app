--liquibase formatted sql

--changeset codex:010-etf-benchmark-navs
CREATE TABLE etf_benchmark_navs (
    fund_code VARCHAR(16) NOT NULL CHECK (fund_code IN ('CSPX', 'IWDA')),
    session_date DATE NOT NULL,
    nav_usd NUMERIC(24, 8) NOT NULL CHECK (nav_usd > 0),
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (fund_code, session_date)
);

--rollback DROP TABLE etf_benchmark_navs;
