-- RSI (recursive self-improvement) experiments: harness genomes, replay
-- evaluations, generations and Pareto archive.
CREATE TABLE rsi_run (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(80) NOT NULL,
    goal VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    generations INT NOT NULL,
    population_size INT NOT NULL,
    best_candidate_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE rsi_generation (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    generation_number INT NOT NULL,
    best_score DOUBLE NOT NULL,
    pareto_json TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_rsi_generation ON rsi_generation (run_id, generation_number);

CREATE TABLE rsi_candidate (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    generation INT NOT NULL,
    parent_id UUID,
    name VARCHAR(200) NOT NULL,
    genome_json TEXT NOT NULL,
    quality DOUBLE NOT NULL,
    cost DOUBLE NOT NULL,
    latency_ms DOUBLE NOT NULL,
    pass_rate DOUBLE NOT NULL,
    scalar_score DOUBLE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_rsi_candidate_run ON rsi_candidate (run_id, generation);

CREATE TABLE rsi_evaluation (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL,
    benchmark_case_id UUID,
    success BOOLEAN NOT NULL,
    duration_ms DOUBLE NOT NULL,
    row_count INT NOT NULL,
    score DOUBLE NOT NULL,
    error TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_rsi_eval_candidate ON rsi_evaluation (candidate_id);
