-- Long-term memory and knowledge promotion. Memory is curated facts/preferences/
-- decisions persisted across sessions; promoting one creates a knowledge entry.
CREATE TABLE agent_memory (
    id UUID PRIMARY KEY,
    scope VARCHAR(20) NOT NULL,
    scope_key VARCHAR(200),
    kind VARCHAR(30) NOT NULL,
    memory_key VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'AGENT',
    confidence INT NOT NULL DEFAULT 70,
    importance INT NOT NULL DEFAULT 50,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    tags VARCHAR(500),
    owner VARCHAR(120),
    use_count INT NOT NULL DEFAULT 0,
    reinforcement_count INT NOT NULL DEFAULT 1,
    last_used_at TIMESTAMP,
    expires_at TIMESTAMP,
    knowledge_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_memory_scope ON agent_memory (scope, scope_key);
CREATE INDEX idx_memory_kind ON agent_memory (kind);
CREATE INDEX idx_memory_status ON agent_memory (status);
CREATE INDEX idx_memory_last_used ON agent_memory (last_used_at);
