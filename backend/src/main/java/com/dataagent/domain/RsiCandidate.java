package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "rsi_candidate")
public class RsiCandidate extends BaseEntity {

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(nullable = false)
    private int generation;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "genome_json", nullable = false, columnDefinition = "TEXT")
    private String genomeJson;

    @Column(nullable = false)
    private double quality;

    @Column(nullable = false)
    private double cost;

    @Column(name = "latency_ms", nullable = false)
    private double latencyMs;

    @Column(name = "pass_rate", nullable = false)
    private double passRate;

    @Column(name = "scalar_score", nullable = false)
    private double scalarScore;

    @Column(nullable = false, length = 20)
    private String status;

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public int getGeneration() {
        return generation;
    }

    public void setGeneration(int generation) {
        this.generation = generation;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGenomeJson() {
        return genomeJson;
    }

    public void setGenomeJson(String genomeJson) {
        this.genomeJson = genomeJson;
    }

    public double getQuality() {
        return quality;
    }

    public void setQuality(double quality) {
        this.quality = quality;
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public double getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(double latencyMs) {
        this.latencyMs = latencyMs;
    }

    public double getPassRate() {
        return passRate;
    }

    public void setPassRate(double passRate) {
        this.passRate = passRate;
    }

    public double getScalarScore() {
        return scalarScore;
    }

    public void setScalarScore(double scalarScore) {
        this.scalarScore = scalarScore;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
