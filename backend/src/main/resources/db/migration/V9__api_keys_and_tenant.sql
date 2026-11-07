-- Tenant isolation, scoped API keys and datasource ACLs.
ALTER TABLE datasource ADD COLUMN tenant_id VARCHAR(80) NOT NULL DEFAULT 'default';

CREATE TABLE api_key (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    key_prefix VARCHAR(24) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    tenant_id VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_api_key_prefix ON api_key (key_prefix);
CREATE INDEX idx_api_key_tenant ON api_key (tenant_id);

CREATE TABLE api_key_datasource (
    api_key_id UUID NOT NULL,
    datasource_id UUID NOT NULL,
    PRIMARY KEY (api_key_id, datasource_id)
);
