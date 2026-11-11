package com.dataagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "rsi_run")
public class RsiRun extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;

    @Column(nullable = false, length = 1000)
    private String goal;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int generations;

    @Column(name = "population_size", nullable = false)
    private int populationSize;

    @Column(name = "best_candidate_id")
    private UUID bestCandidateId;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getGenerations() {
        return generations;
    }

    public void setGenerations(int generations) {
        this.generations = generations;
    }

    public int getPopulationSize() {
        return populationSize;
    }

    public void setPopulationSize(int populationSize) {
        this.populationSize = populationSize;
    }

    public UUID getBestCandidateId() {
        return bestCandidateId;
    }

    public void setBestCandidateId(UUID bestCandidateId) {
        this.bestCandidateId = bestCandidateId;
    }
}
