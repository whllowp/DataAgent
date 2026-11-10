-- Derived and ingested lineage edges, field mappings and OpenLineage events.
CREATE TABLE lineage_edge (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL,
    source_table VARCHAR(300) NOT NULL,
    target_table VARCHAR(300) NOT NULL,
    kind VARCHAR(30) NOT NULL,
    job_name VARCHAR(300),
    workflow_id UUID,
    task_id UUID,
    run_id VARCHAR(200),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_lineage_edge_source ON lineage_edge (tenant_id, source_table);
CREATE INDEX idx_lineage_edge_target ON lineage_edge (tenant_id, target_table);
CREATE INDEX idx_lineage_edge_kind ON lineage_edge (kind);

CREATE TABLE lineage_field_edge (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL,
    source_table VARCHAR(300),
    source_column VARCHAR(300),
    target_table VARCHAR(300) NOT NULL,
    target_column VARCHAR(300) NOT NULL,
    expression TEXT,
    kind VARCHAR(30) NOT NULL,
    job_name VARCHAR(300),
    workflow_id UUID,
    task_id UUID,
    run_id VARCHAR(200),
    wildcard BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_field_edge_source ON lineage_field_edge (tenant_id, source_table, source_column);
CREATE INDEX idx_field_edge_target ON lineage_field_edge (tenant_id, target_table, target_column);

CREATE TABLE openlineage_event (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL,
    event_time TIMESTAMP,
    event_type VARCHAR(20) NOT NULL,
    job_namespace VARCHAR(300),
    job_name VARCHAR(300),
    run_id VARCHAR(200),
    producer VARCHAR(300),
    schema_url VARCHAR(500),
    inputs_json TEXT,
    outputs_json TEXT,
    column_lineage_json TEXT,
    raw_event TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ol_event_job ON openlineage_event (tenant_id, job_name);
CREATE INDEX idx_ol_event_created ON openlineage_event (created_at);
