package com.dataagent.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RsiDtos {

    private RsiDtos() {
    }

    public record StartRequest(
            String goal,
            Integer generations,
            Integer populationSize
    ) {
    }

    public record RunView(
            UUID id,
            String goal,
            String status,
            int generations,
            int populationSize,
            UUID bestCandidateId,
            double bestQuality,
            double bestCost,
            Instant createdAt
    ) {
    }

    public record CandidateView(
            UUID id,
            int generation,
            UUID parentId,
            String name,
            Map<String, Object> genome,
            double quality,
            double cost,
            double latencyMs,
            double passRate,
            double scalarScore,
            String status
    ) {
    }

    public record GenerationView(
            int generation,
            double bestScore,
            List<CandidateView> population
    ) {
    }

    public record RunDetail(
            RunView run,
            List<GenerationView> generations,
            CandidateView best,
            List<CandidateView> paretoFront
    ) {
    }

    public record Overview(
            long runs,
            long candidates,
            double bestQuality,
            double bestCost,
            List<RunView> recent
    ) {
    }
}
