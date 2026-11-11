package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "rsi_generation")
public class RsiGeneration extends BaseEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "generation_number", nullable = false)
    private int generationNumber;

    @Column(name = "best_score", nullable = false)
    private double bestScore;

    @Column(name = "pareto_json", columnDefinition = "TEXT")
    private String paretoJson;

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public int getGenerationNumber() {
        return generationNumber;
    }

    public void setGenerationNumber(int generationNumber) {
        this.generationNumber = generationNumber;
    }

    public double getBestScore() {
        return bestScore;
    }

    public void setBestScore(double bestScore) {
        this.bestScore = bestScore;
    }

    public String getParetoJson() {
        return paretoJson;
    }

    public void setParetoJson(String paretoJson) {
        this.paretoJson = paretoJson;
    }
}
