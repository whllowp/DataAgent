-- Immutable governance trail for SQL, datasource, semantic and memory actions.
CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor VARCHAR(120) NOT NULL,
    action VARCHAR(60) NOT NULL,
    resource_type VARCHAR(60),
    resource_id VARCHAR(200),
    datasource_id UUID,
    status VARCHAR(20) NOT NULL,
    detail TEXT,
    client_ip VARCHAR(64),
    user_agent VARCHAR(300),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_audit_created ON audit_event (created_at);
CREATE INDEX idx_audit_actor ON audit_event (actor);
CREATE INDEX idx_audit_action ON audit_event (action);
CREATE INDEX idx_audit_datasource ON audit_event (datasource_id);
