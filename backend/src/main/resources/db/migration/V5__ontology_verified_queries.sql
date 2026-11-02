-- Genie-style ontology and verified-query catalog.
CREATE TABLE semantic_relationship (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    from_entity_id UUID NOT NULL,
    to_entity_id UUID NOT NULL,
    relationship_type VARCHAR(40) NOT NULL,
    join_condition TEXT NOT NULL,
    description TEXT,
    synonyms VARCHAR(1000),
    owner VARCHAR(120),
    tags VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_relationship_name ON semantic_relationship (name);
CREATE INDEX idx_relationship_from ON semantic_relationship (from_entity_id);
CREATE INDEX idx_relationship_to ON semantic_relationship (to_entity_id);

CREATE TABLE verified_query (
    id UUID PRIMARY KEY,
    question VARCHAR(1000) NOT NULL,
    sql_text TEXT NOT NULL,
    datasource_id UUID,
    metric_id UUID,
    entity_id UUID,
    tags VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED',
    owner VARCHAR(120),
    notes TEXT,
    expected_columns VARCHAR(2000),
    use_count INT NOT NULL DEFAULT 0,
    last_validated_at TIMESTAMP,
    last_validation_status VARCHAR(20),
    last_duration_ms BIGINT,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_verified_status ON verified_query (status);
CREATE INDEX idx_verified_metric ON verified_query (metric_id);
CREATE INDEX idx_verified_entity ON verified_query (entity_id);
