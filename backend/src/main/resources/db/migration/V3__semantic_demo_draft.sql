-- Demo semantic definitions are examples, not approved business definitions.
-- Mark demo-owned rows as DRAFT so teams must review/replace before production use.
UPDATE semantic_entity
SET status = 'DRAFT', updated_at = CURRENT_TIMESTAMP
WHERE owner = 'demo' AND status = 'ACTIVE';

UPDATE semantic_metric
SET status = 'DRAFT', updated_at = CURRENT_TIMESTAMP
WHERE owner = 'demo' AND status = 'ACTIVE';

UPDATE semantic_term
SET status = 'DRAFT', updated_at = CURRENT_TIMESTAMP
WHERE owner = 'demo' AND status = 'ACTIVE';
