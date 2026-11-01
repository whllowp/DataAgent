package com.dataagent.web;

import com.dataagent.service.SemanticService;
import com.dataagent.service.SemanticSyncService;
import com.dataagent.web.dto.KnowledgeDtos;
import com.dataagent.web.dto.SemanticDtos;
import com.dataagent.web.dto.SqlAssetDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/semantic")
public class SemanticController {

    private final SemanticService semantic;
    private final SemanticSyncService sync;

    public SemanticController(SemanticService semantic, SemanticSyncService sync) {
        this.semantic = semantic;
        this.sync = sync;
    }

    @GetMapping("/overview")
    public SemanticDtos.SemanticOverview overview() {
        return semantic.overview();
    }

    @GetMapping("/entities")
    public List<SemanticDtos.EntityView> entities(@RequestParam(required = false, name = "q") String query,
                                                  @RequestParam(required = false) String domain) {
        return semantic.listEntities(query, domain);
    }

    @PostMapping("/entities")
    @ResponseStatus(HttpStatus.CREATED)
    public SemanticDtos.EntityView createEntity(@Valid @RequestBody SemanticDtos.EntityRequest request) {
        return semantic.createEntity(request);
    }

    @PutMapping("/entities/{id}")
    public SemanticDtos.EntityView updateEntity(@PathVariable UUID id,
                                                @Valid @RequestBody SemanticDtos.EntityRequest request) {
        return semantic.updateEntity(id, request);
    }

    @DeleteMapping("/entities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntity(@PathVariable UUID id) {
        semantic.deleteEntity(id);
    }

    @GetMapping("/metrics")
    public List<SemanticDtos.MetricView> metrics(@RequestParam(required = false, name = "q") String query) {
        return semantic.listMetrics(query);
    }

    @PostMapping("/metrics")
    @ResponseStatus(HttpStatus.CREATED)
    public SemanticDtos.MetricView createMetric(@Valid @RequestBody SemanticDtos.MetricRequest request) {
        return semantic.createMetric(request);
    }

    @PutMapping("/metrics/{id}")
    public SemanticDtos.MetricView updateMetric(@PathVariable UUID id,
                                                @Valid @RequestBody SemanticDtos.MetricRequest request) {
        return semantic.updateMetric(id, request);
    }

    @DeleteMapping("/metrics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMetric(@PathVariable UUID id) {
        semantic.deleteMetric(id);
    }

    @GetMapping("/terms")
    public List<SemanticDtos.TermView> terms(@RequestParam(required = false, name = "q") String query) {
        return semantic.listTerms(query);
    }

    @PostMapping("/terms")
    @ResponseStatus(HttpStatus.CREATED)
    public SemanticDtos.TermView createTerm(@Valid @RequestBody SemanticDtos.TermRequest request) {
        return semantic.createTerm(request);
    }

    @PutMapping("/terms/{id}")
    public SemanticDtos.TermView updateTerm(@PathVariable UUID id,
                                            @Valid @RequestBody SemanticDtos.TermRequest request) {
        return semantic.updateTerm(id, request);
    }

    @DeleteMapping("/terms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTerm(@PathVariable UUID id) {
        semantic.deleteTerm(id);
    }

    @GetMapping("/context")
    public SemanticDtos.SemanticContext context(@RequestParam(required = false, name = "q") String query,
                                                @RequestParam(required = false) String domain) {
        return semantic.context(query, domain);
    }

    @PostMapping("/design")
    public SemanticDtos.DesignView design(@Valid @RequestBody SemanticDtos.DesignRequest request) {
        return semantic.design(request);
    }

    @GetMapping("/links")
    public List<SemanticDtos.LinkView> links(@RequestParam String targetType,
                                             @RequestParam UUID targetId) {
        return semantic.linksForTarget(targetType, targetId);
    }

    @GetMapping("/links/source")
    public List<SemanticDtos.LinkView> sourceLinks(@RequestParam String sourceType,
                                                   @RequestParam UUID sourceId) {
        return semantic.linksForSource(sourceType, sourceId);
    }

    @GetMapping("/{targetType}/{targetId}/knowledge")
    public List<KnowledgeDtos.KnowledgeView> relatedKnowledge(@PathVariable String targetType,
                                                              @PathVariable UUID targetId) {
        return semantic.relatedKnowledge(targetType, targetId);
    }

    @GetMapping("/{targetType}/{targetId}/assets")
    public List<SqlAssetDtos.SqlAssetView> relatedAssets(@PathVariable String targetType,
                                                         @PathVariable UUID targetId) {
        return semantic.relatedAssets(targetType, targetId);
    }

    @PostMapping("/sync")
    public SemanticDtos.SyncRunView sync() {
        return sync.sync("MANUAL");
    }

    @GetMapping("/sync/runs")
    public List<SemanticDtos.SyncRunView> syncRuns() {
        return semantic.overview().recentRuns();
    }
}
