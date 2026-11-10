-- ETL task/workflow topology, including DolphinScheduler task relations and
-- cross-workflow dependencies.
CREATE TABLE etl_workflow (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',
    source VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    external_id VARCHAR(200),
    project VARCHAR(120),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    schedule_cron VARCHAR(120),
    timezone VARCHAR(80),
    release_state VARCHAR(20),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    owner VARCHAR(120),
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_etl_workflow_external ON etl_workflow (tenant_id, source, external_id);
CREATE INDEX idx_etl_workflow_tenant ON etl_workflow (tenant_id);

CREATE TABLE etl_task (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL,
    external_id VARCHAR(200),
    name VARCHAR(200) NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    order_index INT NOT NULL DEFAULT 0,
    sql_text TEXT,
    source_tables TEXT,
    target_table VARCHAR(300),
    datasource_id UUID,
    config_json TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_etl_task_workflow ON etl_task (workflow_id);
CREATE INDEX idx_etl_task_target ON etl_task (target_table);

CREATE TABLE etl_task_dependency (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL,
    upstream_task_id UUID NOT NULL,
    downstream_task_id UUID NOT NULL,
    condition_type VARCHAR(40) NOT NULL DEFAULT 'ALL_SUCCESS',
    lag_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_etl_task_dep ON etl_task_dependency (upstream_task_id, downstream_task_id);
CREATE INDEX idx_etl_task_dep_workflow ON etl_task_dependency (workflow_id);

CREATE TABLE etl_workflow_dependency (
    id UUID PRIMARY KEY,
    upstream_workflow_id UUID NOT NULL,
    downstream_workflow_id UUID NOT NULL,
    condition_type VARCHAR(40) NOT NULL DEFAULT 'ALL_SUCCESS',
    lag_seconds BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_etl_workflow_dep ON etl_workflow_dependency (upstream_workflow_id, downstream_workflow_id);
