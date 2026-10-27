CREATE TABLE datasource (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    dialect VARCHAR(32) NOT NULL,
    jdbc_url VARCHAR(1000) NOT NULL,
    username VARCHAR(200),
    password_cipher VARCHAR(2000),
    read_only BOOLEAN NOT NULL DEFAULT TRUE,
    max_rows INTEGER NOT NULL DEFAULT 500,
    query_timeout_seconds INTEGER NOT NULL DEFAULT 15,
    description VARCHAR(1000),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE query_execution (
    id UUID PRIMARY KEY,
    datasource_id UUID NOT NULL,
    user_id VARCHAR(120) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    sql_text TEXT NOT NULL,
    sql_fingerprint VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    fetch_ms BIGINT NOT NULL DEFAULT 0,
    row_count INTEGER NOT NULL DEFAULT 0,
    truncated BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    plan_raw TEXT,
    plan_format VARCHAR(20),
    problem VARCHAR(2000),
    tags VARCHAR(500),
    solved BOOLEAN NOT NULL DEFAULT FALSE,
    knowledge_id UUID,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_query_created ON query_execution (created_at);
CREATE INDEX idx_query_datasource ON query_execution (datasource_id);
CREATE INDEX idx_query_fingerprint ON query_execution (sql_fingerprint);
CREATE INDEX idx_query_solved ON query_execution (solved);

CREATE TABLE knowledge_entry (
    id UUID PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    problem TEXT,
    sql_text TEXT NOT NULL,
    optimized_sql TEXT,
    dialect VARCHAR(32),
    tags VARCHAR(500),
    plan_summary TEXT,
    root_cause TEXT,
    optimization_steps TEXT,
    before_duration_ms BIGINT,
    after_duration_ms BIGINT,
    author VARCHAR(120),
    source_query_id UUID,
    usage_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_knowledge_updated ON knowledge_entry (updated_at);
CREATE INDEX idx_knowledge_dialect ON knowledge_entry (dialect);

CREATE TABLE optimization_experiment (
    id UUID PRIMARY KEY,
    datasource_id UUID NOT NULL,
    original_sql TEXT NOT NULL,
    candidate_sql TEXT NOT NULL,
    original_ms BIGINT,
    candidate_ms BIGINT,
    original_plan TEXT,
    candidate_plan TEXT,
    verdict VARCHAR(40),
    notes TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_experiment_created ON optimization_experiment (created_at);
