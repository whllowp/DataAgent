-- SQL asset cache: every SQL developers write / execute, plus the business problem it solves.
CREATE TABLE sql_asset (
    id UUID PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    sql_text TEXT NOT NULL,
    business_problem TEXT,
    domain VARCHAR(120),
    tags VARCHAR(500),
    datasource_id UUID,
    author VARCHAR(120),
    source VARCHAR(32) NOT NULL,
    execution_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    avg_duration_ms BIGINT,
    last_duration_ms BIGINT,
    last_plan TEXT,
    last_plan_format VARCHAR(20),
    knowledge_id UUID,
    first_seen TIMESTAMP NOT NULL,
    last_seen TIMESTAMP NOT NULL,
    semantic_synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_sql_asset_fingerprint ON sql_asset (fingerprint);
CREATE INDEX idx_sql_asset_last_seen ON sql_asset (last_seen);
CREATE INDEX idx_sql_asset_domain ON sql_asset (domain);
CREATE INDEX idx_sql_asset_knowledge ON sql_asset (knowledge_id);

-- Semantic layer: business entities, metrics, glossary terms.
CREATE TABLE semantic_entity (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    domain VARCHAR(120),
    description TEXT,
    physical_tables VARCHAR(1000),
    synonyms VARCHAR(1000),
    owner VARCHAR(120),
    tags VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_semantic_entity_name ON semantic_entity (name);
CREATE INDEX idx_semantic_entity_domain ON semantic_entity (domain);

CREATE TABLE semantic_metric (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    display_name VARCHAR(240) NOT NULL,
    entity_id UUID,
    business_definition TEXT,
    expression TEXT,
    dimensions VARCHAR(1000),
    synonyms VARCHAR(1000),
    owner VARCHAR(120),
    tags VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_semantic_metric_name ON semantic_metric (name);
CREATE INDEX idx_semantic_metric_entity ON semantic_metric (entity_id);

CREATE TABLE semantic_term (
    id UUID PRIMARY KEY,
    term VARCHAR(160) NOT NULL,
    definition TEXT,
    synonyms VARCHAR(1000),
    related_entity_id UUID,
    related_metric_id UUID,
    owner VARCHAR(120),
    tags VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_semantic_term_term ON semantic_term (term);

-- Links are produced by the periodic semantic <-> knowledge sync (AUTO) or curated by users (MANUAL).
CREATE TABLE semantic_link (
    id UUID PRIMARY KEY,
    source_type VARCHAR(20) NOT NULL,
    source_id UUID NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id UUID NOT NULL,
    confidence INTEGER NOT NULL,
    reason VARCHAR(1000),
    link_source VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_semantic_link UNIQUE (source_type, source_id, target_type, target_id)
);

CREATE INDEX idx_semantic_link_source ON semantic_link (source_type, source_id);
CREATE INDEX idx_semantic_link_target ON semantic_link (target_type, target_id);

CREATE TABLE semantic_sync_run (
    id UUID PRIMARY KEY,
    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    scanned_assets INTEGER NOT NULL DEFAULT 0,
    scanned_knowledge INTEGER NOT NULL DEFAULT 0,
    created_links INTEGER NOT NULL DEFAULT 0,
    updated_links INTEGER NOT NULL DEFAULT 0,
    removed_links INTEGER NOT NULL DEFAULT 0,
    message TEXT
);

CREATE INDEX idx_semantic_sync_started ON semantic_sync_run (started_at);
