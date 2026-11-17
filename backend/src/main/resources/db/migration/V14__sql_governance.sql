-- SQL governance: ten-round optimization evolution runs.
-- One row per optimize/evolve run; safe to add on H2/PostgreSQL/MySQL.

CREATE TABLE sql_optimization_run (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',
    datasource_id UUID,
    original_sql TEXT NOT NULL,
    best_sql TEXT,
    baseline_ms BIGINT,
    best_ms BIGINT,
    improvement_pct DOUBLE,
    score INT NOT NULL DEFAULT 0,
    rounds INT NOT NULL DEFAULT 10,
    verdict VARCHAR(40),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_sql_opt_run_ds ON sql_optimization_run (datasource_id, created_at);
CREATE INDEX idx_sql_opt_run_tenant ON sql_optimization_run (tenant_id, created_at);
