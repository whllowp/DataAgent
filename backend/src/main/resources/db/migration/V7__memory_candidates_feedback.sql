-- Review queue for memories extracted from finished conversations, plus
-- explicit user-feedback corrections that feed memory + verified queries.
CREATE TABLE memory_candidate (
    id UUID PRIMARY KEY,
    session_id VARCHAR(200),
    question VARCHAR(2000),
    source_text TEXT,
    scope VARCHAR(20) NOT NULL,
    scope_key VARCHAR(200),
    kind VARCHAR(30) NOT NULL,
    memory_key VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    confidence INT NOT NULL DEFAULT 60,
    importance INT NOT NULL DEFAULT 50,
    tags VARCHAR(500),
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    memory_id UUID,
    reviewed_by VARCHAR(120),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_candidate_hash ON memory_candidate (content_hash);
CREATE INDEX idx_candidate_status ON memory_candidate (status);

CREATE TABLE agent_feedback (
    id UUID PRIMARY KEY,
    session_id VARCHAR(200),
    question VARCHAR(2000),
    original_sql TEXT,
    corrected_sql TEXT,
    correction TEXT,
    rating INT,
    notes TEXT,
    datasource_id UUID,
    metric_id UUID,
    entity_id UUID,
    memory_id UUID,
    verified_query_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_feedback_created ON agent_feedback (created_at);
