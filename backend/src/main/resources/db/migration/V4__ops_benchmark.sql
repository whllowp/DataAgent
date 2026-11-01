-- SRE / ops SQL benchmark dataset. Cases are periodically executed (read-only) and their
-- latest status/timing feeds the ops dashboard and semantic sync context.
CREATE TABLE sql_benchmark_case (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    domain VARCHAR(120),
    description TEXT,
    sql_text TEXT NOT NULL,
    datasource_id UUID,
    baseline_ms BIGINT,
    threshold_ms BIGINT,
    tags VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_run_at TIMESTAMP,
    last_duration_ms BIGINT,
    last_status VARCHAR(20),
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_benchmark_name ON sql_benchmark_case (name);
CREATE INDEX idx_benchmark_status ON sql_benchmark_case (status);
CREATE INDEX idx_benchmark_last_run ON sql_benchmark_case (last_run_at);
