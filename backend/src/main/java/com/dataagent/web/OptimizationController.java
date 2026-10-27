package com.dataagent.web;

import com.dataagent.service.OptimizationService;
import com.dataagent.web.dto.OptimizeDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/optimize")
public class OptimizationController {

    private final OptimizationService optimizer;

    public OptimizationController(OptimizationService optimizer) {
        this.optimizer = optimizer;
    }

    @PostMapping("/analyze")
    public OptimizeDtos.AnalysisView analyze(@Valid @RequestBody OptimizeDtos.AnalyzeRequest request) {
        return optimizer.analyze(request.datasourceId(), request.sql(),
                request.includePlan() == null || request.includePlan());
    }

    @PostMapping("/compare")
    public OptimizeDtos.CompareView compare(@Valid @RequestBody OptimizeDtos.CompareRequest request) {
        return optimizer.compare(request.datasourceId(), request.originalSql(), request.candidateSql(),
                request.runs() == null ? 2 : request.runs(),
                request.execute() == null || request.execute(), request.notes());
    }

    @PostMapping("/prompt")
    public OptimizeDtos.PromptView prompt(@Valid @RequestBody OptimizeDtos.AnalyzeRequest request) {
        return optimizer.prompt(request.datasourceId(), request.sql());
    }
}
