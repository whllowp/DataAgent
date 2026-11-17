-- Data export + long-term knowledge accumulation.
-- Safe to run on H2/PostgreSQL/MySQL.

CREATE TABLE data_export (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',
    datasource_id UUID,
    sql_text TEXT NOT NULL,
    sql_fingerprint VARCHAR(64),
    format VARCHAR(10) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    row_count INT NOT NULL DEFAULT 0,
    byte_size BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    created_by VARCHAR(120),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_data_export_tenant ON data_export (tenant_id, created_at);
CREATE INDEX idx_data_export_ds ON data_export (datasource_id, created_at);

CREATE TABLE knowledge_accumulation_run (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',
    trigger_type VARCHAR(20) NOT NULL,
    min_frequency INT NOT NULL DEFAULT 3,
    promoted INT NOT NULL DEFAULT 0,
    merged INT NOT NULL DEFAULT 0,
    deduped INT NOT NULL DEFAULT 0,
    total_knowledge INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_knowledge_accum_tenant ON knowledge_accumulation_run (tenant_id, created_at);
