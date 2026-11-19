package com.dataagent.web;

import com.dataagent.service.ExportAccumulationLabService;
import com.dataagent.service.KnowledgeExtractionLabService;
import com.dataagent.service.MemoryEvolutionService;
import com.dataagent.service.OntologyEvolutionService;
import com.dataagent.service.PerformanceLabService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Evolution APIs for the memory hierarchy and ontology/lineage layer.
 */
@RestController
@RequestMapping("/api/evolution")
public class EvolutionController {

    private final MemoryEvolutionService memory;
    private final OntologyEvolutionService ontology;
    private final PerformanceLabService performance;
    private final ExportAccumulationLabService exportLab;
    private final KnowledgeExtractionLabService extractionLab;

    public EvolutionController(MemoryEvolutionService memory, OntologyEvolutionService ontology,
                               PerformanceLabService performance, ExportAccumulationLabService exportLab,
                               KnowledgeExtractionLabService extractionLab) {
        this.memory = memory;
        this.ontology = ontology;
        this.performance = performance;
        this.exportLab = exportLab;
        this.extractionLab = extractionLab;
    }

    @GetMapping("/memory/index")
    public MemoryEvolutionService.IndexView memoryIndex(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false, defaultValue = "1600") int budget) {
        return memory.index(query, budget);
    }

    @GetMapping("/memory/transcripts")
    public List<MemoryEvolutionService.TranscriptHit> transcripts(
            @RequestParam(name = "q") String query,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return memory.searchTranscripts(query, limit);
    }

    @GetMapping("/memory/citations")
    public MemoryEvolutionService.CitationAudit citations() {
        return memory.auditCitations();
    }

    @PostMapping("/memory/citations/repair")
    public MemoryEvolutionService.CitationRepair repairCitations() {
        return memory.repairCitations();
    }

    @GetMapping("/memory/rounds")
    public MemoryEvolutionService.EvolutionReport memoryRounds(
            @RequestParam(required = false, defaultValue = "10") int count) {
        return memory.evolution(count);
    }

    @GetMapping("/memory/retrieval")
    public MemoryEvolutionService.RetrievalReport retrieval(
            @RequestParam(required = false, defaultValue = "10") int probes) {
        return memory.retrievalEval(probes);
    }

    @GetMapping("/memory/accuracy")
    public MemoryEvolutionService.AccuracyReport accuracy(
            @RequestParam(required = false, defaultValue = "10") int probes) {
        return memory.accuracy(probes);
    }

    @GetMapping("/memory/contradictions")
    public java.util.List<MemoryEvolutionService.Contradiction> contradictions() {
        return memory.contradictions();
    }

    @GetMapping("/memory/compress")
    public MemoryEvolutionService.Compression compress(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false, defaultValue = "1600") int budget) {
        return memory.compress(query, budget);
    }

    @GetMapping("/memory/rounds-v2")
    public MemoryEvolutionService.EvolutionReport memoryRoundsV2(
            @RequestParam(required = false, defaultValue = "10") int count) {
        return memory.evolutionV2(count);
    }

    @PostMapping("/semantic/infer-joins")
    public OntologyEvolutionService.JoinInference inferJoins() {
        return ontology.inferJoinsFromHistory();
    }

    @GetMapping("/semantic/bridges")
    public List<OntologyEvolutionService.BridgeTable> bridges() {
        return ontology.bridges();
    }

    @GetMapping("/semantic/drift")
    public OntologyEvolutionService.DriftReport drift(
            @RequestParam UUID datasourceId,
            @RequestParam(required = false) String schema) {
        return ontology.drift(datasourceId, schema);
    }

    @GetMapping("/semantic/answerable-v2")
    public OntologyEvolutionService.AnswerabilityV2 answerableV2(@RequestParam(name = "q") String question) {
        return ontology.answerableV2(question);
    }

    @GetMapping("/semantic/paths")
    public java.util.List<OntologyEvolutionService.RankedPath> paths(
            @RequestParam UUID from,
            @RequestParam UUID to,
            @RequestParam(required = false, defaultValue = "5") int limit) {
        return ontology.bestPaths(from, to, limit);
    }

    @GetMapping("/semantic/rounds-v2")
    public OntologyEvolutionService.EvolutionReport semanticRoundsV2(
            @RequestParam(required = false, defaultValue = "10") int count) {
        return ontology.evolutionV2(count);
    }

    @GetMapping("/perf")
    public PerformanceLabService.Report performance(
            @RequestParam(required = false, defaultValue = "10") int rounds) {
        return performance.run(rounds);
    }

    @GetMapping("/export-lab")
    public ExportAccumulationLabService.Report exportLab(
            @RequestParam(required = false, defaultValue = "10") int rounds) {
        return exportLab.run(rounds);
    }

    @GetMapping("/extraction-lab")
    public KnowledgeExtractionLabService.Report extractionLab(
            @RequestParam(required = false, defaultValue = "10") int rounds) {
        return extractionLab.run(rounds);
    }

    @GetMapping("/semantic/lineage")
    public OntologyEvolutionService.SemanticPath lineage(
            @RequestParam String table,
            @RequestParam(required = false) String column) {
        return ontology.lineagePath(table, column);
    }

    @GetMapping("/semantic/rounds")
    public OntologyEvolutionService.EvolutionReport semanticRounds(
            @RequestParam(required = false, defaultValue = "10") int count) {
        return ontology.evolution(count);
    }
}
