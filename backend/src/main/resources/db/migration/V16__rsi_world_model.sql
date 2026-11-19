-- RSI-Anything world-model columns: prediction-guided selection keeps a
-- predicted scalar score on each candidate, and each generation records the
-- world model's serialized state, its prediction error and reliability so the
-- recursive self-improvement loop is observable after the run.
ALTER TABLE rsi_candidate ADD COLUMN predicted_score DOUBLE;

ALTER TABLE rsi_generation ADD COLUMN world_model_json TEXT;
ALTER TABLE rsi_generation ADD COLUMN prediction_mae DOUBLE;
ALTER TABLE rsi_generation ADD COLUMN reliability VARCHAR(20);
