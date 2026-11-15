-- Memory hierarchy + dreaming, and Auto-Ontology style semantic compilation.
-- Existing memory notes are backfilled into tenant default; new tables are
-- tenant-scoped and safe to add on H2/PostgreSQL/MySQL.

ALTER TABLE agent_memory ADD COLUMN tenant_id VARCHAR(80) NOT NULL DEFAULT 'default';
ALTER TABLE agent_memory ADD COLUMN layer VARCHAR(20) NOT NULL DEFAULT 'NOTE';
ALTER TABLE agent_memory ADD COLUMN salience INT NOT NULL DEFAULT 50;
ALTER TABLE agent_memory ADD COLUMN validated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE agent_memory ADD COLUMN last_validated_at TIMESTAMP;
ALTER TABLE agent_memory ADD COLUMN evidence TEXT;
ALTER TABLE agent_memory ADD COLUMN supersedes_id UUID;
ALTER TABLE agent_memory ADD COLUMN wiki_slug VARCHAR(200);

CREATE INDEX idx_memory_tenant ON agent_memory (tenant_id);
CREATE INDEX idx_memory_salience ON agent_memory (tenant_id, status, salience);

ALTER TABLE memory_candidate ADD COLUMN tenant_id VARCHAR(80) NOT NULL DEFAULT 'default';
CREATE INDEX idx_memory_candidate_tenant ON memory_candidate (tenant_id, status);

CREATE TABLE memory_wiki_page (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    title VARCHAR(240) NOT NULL,
    summary TEXT,
    content TEXT NOT NULL,
    page_type VARCHAR(30) NOT NULL DEFAULT 'TOPIC',
    token_estimate INT NOT NULL DEFAULT 0,
    source_memory_ids TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_memory_wiki_page ON memory_wiki_page (tenant_id, slug);
CREATE INDEX idx_memory_wiki_type ON memory_wiki_page (tenant_id, page_type, status);

CREATE TABLE memory_wiki_link (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL,
    from_page_id UUID NOT NULL,
    to_page_id UUID NOT NULL,
    relation VARCHAR(40) NOT NULL DEFAULT 'RELATED',
    confidence INT NOT NULL DEFAULT 70,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_memory_wiki_link_from ON memory_wiki_link (from_page_id);
CREATE INDEX idx_memory_wiki_link_to ON memory_wiki_link (to_page_id);

CREATE TABLE memory_dream_run (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL,
    trigger_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    scanned_transcripts INT NOT NULL DEFAULT 0,
    scanned_memories INT NOT NULL DEFAULT 0,
    merged_memories INT NOT NULL DEFAULT 0,
    corrected_memories INT NOT NULL DEFAULT 0,
    promoted_notes INT NOT NULL DEFAULT 0,
    wiki_pages INT NOT NULL DEFAULT 0,
    wiki_links INT NOT NULL DEFAULT 0,
    context_tokens INT NOT NULL DEFAULT 0,
    message TEXT,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_memory_dream_tenant ON memory_dream_run (tenant_id, started_at);

CREATE TABLE memory_context_snapshot (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL,
    session_id VARCHAR(200),
    query_text VARCHAR(2000),
    layer VARCHAR(30) NOT NULL DEFAULT 'INJECTED',
    token_estimate INT NOT NULL DEFAULT 0,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_memory_context_tenant ON memory_context_snapshot (tenant_id, created_at);

CREATE TABLE semantic_term_column (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',
    term_id UUID NOT NULL,
    datasource_id UUID,
    schema_name VARCHAR(200),
    table_name VARCHAR(200) NOT NULL,
    column_name VARCHAR(200) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'ATTRIBUTE',
    confidence INT NOT NULL DEFAULT 70,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    provenance TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_term_column_term ON semantic_term_column (term_id);
CREATE INDEX idx_term_column_physical ON semantic_term_column (table_name, column_name);

CREATE TABLE semantic_sql_attribute (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',
    term_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    display_name VARCHAR(240) NOT NULL,
    expression TEXT NOT NULL,
    business_definition TEXT,
    grain VARCHAR(200),
    synonyms VARCHAR(1000),
    owner VARCHAR(120),
    tags VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    confidence INT NOT NULL DEFAULT 70,
    source VARCHAR(30) NOT NULL DEFAULT 'COMPILED',
    verified_query_id UUID,
    usage_count INT NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP,
    provenance TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_sql_attribute_term ON semantic_sql_attribute (term_id, status);

CREATE TABLE semantic_join (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',
    name VARCHAR(200) NOT NULL,
    from_term_id UUID NOT NULL,
    to_term_id UUID NOT NULL,
    join_condition TEXT NOT NULL,
    relationship_type VARCHAR(40) NOT NULL DEFAULT 'MANY_TO_ONE',
    confidence INT NOT NULL DEFAULT 70,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    source VARCHAR(30) NOT NULL DEFAULT 'INFERRED',
    provenance TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_semantic_join_from ON semantic_join (from_term_id);
CREATE INDEX idx_semantic_join_to ON semantic_join (to_term_id);

CREATE TABLE semantic_compile_run (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL DEFAULT 'default',
    datasource_id UUID,
    schema_name VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    scanned_tables INT NOT NULL DEFAULT 0,
    scanned_columns INT NOT NULL DEFAULT 0,
    created_terms INT NOT NULL DEFAULT 0,
    mapped_columns INT NOT NULL DEFAULT 0,
    created_joins INT NOT NULL DEFAULT 0,
    created_attributes INT NOT NULL DEFAULT 0,
    message TEXT,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_semantic_compile_tenant ON semantic_compile_run (tenant_id, started_at);
