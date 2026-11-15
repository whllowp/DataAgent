package com.dataagent.web;

import com.dataagent.service.MemoryEvolutionService;
import com.dataagent.service.OntologyEvolutionService;
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

    public EvolutionController(MemoryEvolutionService memory, OntologyEvolutionService ontology) {
        this.memory = memory;
        this.ontology = ontology;
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
