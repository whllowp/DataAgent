package com.dataagent.web;

import com.dataagent.service.OptimizationService;
import com.dataagent.service.SqlOptimizationLabService;
import com.dataagent.sql.SqlRewriter;
import com.dataagent.sql.SqlRiskAssessor;
import com.dataagent.web.dto.OptimizeDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/optimize")
public class OptimizationController {

    private final OptimizationService optimizer;
    private final SqlRiskAssessor riskAssessor;
    private final SqlRewriter rewriter;
    private final SqlOptimizationLabService optimizationLab;

    public OptimizationController(OptimizationService optimizer, SqlRiskAssessor riskAssessor,
                                  SqlRewriter rewriter, SqlOptimizationLabService optimizationLab) {
        this.optimizer = optimizer;
        this.riskAssessor = riskAssessor;
        this.rewriter = rewriter;
        this.optimizationLab = optimizationLab;
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

    @PostMapping("/risk")
    public OptimizeDtos.RiskView risk(@Valid @RequestBody OptimizeDtos.RewriteRequest request) {
        SqlRiskAssessor.RiskAssessment assessment = riskAssessor.assess(request.sql());
        return new OptimizeDtos.RiskView(assessment.score(), assessment.level(), assessment.blocked(),
                assessment.risks().stream()
                        .map(r -> new OptimizeDtos.Risk(r.code(), r.severity(), r.weight(), r.title(),
                                r.detail(), r.evidence()))
                        .collect(Collectors.toList()));
    }

    @PostMapping("/rewrite")
    public OptimizeDtos.RewriteView rewrite(@Valid @RequestBody OptimizeDtos.RewriteRequest request) {
        SqlRewriter.RewriteResult result = rewriter.rewrite(request.sql());
        return new OptimizeDtos.RewriteView(result.rewrittenSql(),
                result.applied().stream()
                        .map(r -> new OptimizeDtos.Rewrite(r.type(), r.detail(), r.before(), r.after()))
                        .collect(Collectors.toList()),
                result.suggestions(), result.risks());
    }

    @PostMapping("/evolve")
    public OptimizeDtos.EvolutionView evolve(@Valid @RequestBody OptimizeDtos.EvolveRequest request) {
        return optimizationLab.evolve(request.datasourceId(), request.sql(),
                request.rounds() == null ? 10 : request.rounds());
    }
}
