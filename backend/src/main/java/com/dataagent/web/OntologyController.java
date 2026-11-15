package com.dataagent.web;

import com.dataagent.service.OntologyService;
import com.dataagent.web.dto.SemanticDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/semantic")
public class OntologyController {

    private final OntologyService ontology;

    public OntologyController(OntologyService ontology) {
        this.ontology = ontology;
    }

    @PostMapping("/compile")
    @ResponseStatus(HttpStatus.CREATED)
    public SemanticDtos.CompileRunView compile(@Valid @RequestBody SemanticDtos.CompileRequest request) {
        return ontology.compile(request);
    }

    @GetMapping("/compile/runs")
    public List<SemanticDtos.CompileRunView> compileRuns() {
        return ontology.compileRuns();
    }

    @GetMapping("/compile/last")
    public SemanticDtos.CompileRunView lastCompile() {
        return ontology.lastCompile();
    }

    @GetMapping("/ontology/graph")
    public SemanticDtos.OntologyGraph graph(@RequestParam(required = false) UUID termId) {
        return ontology.graph(termId);
    }

    @GetMapping("/ontology/graph/full")
    public SemanticDtos.OntologyGraph fullGraph() {
        return ontology.fullGraph();
    }

    @GetMapping("/ontology/answerable")
    public SemanticDtos.Answerability answerable(@RequestParam(name = "q") String question) {
        return ontology.answerable(question);
    }

    @GetMapping("/ontology/joins/path")
    public List<SemanticDtos.JoinView> joinPath(@RequestParam UUID from, @RequestParam UUID to) {
        return ontology.joinPath(from, to);
    }

    @GetMapping("/ontology/columns")
    public List<SemanticDtos.TermColumnView> columns(
            @RequestParam(required = false) UUID termId,
            @RequestParam(required = false) String table) {
        if (termId != null) {
            return ontology.columnsForTerm(termId);
        }
        if (table != null && !table.isBlank()) {
            return ontology.columnsForTable(table);
        }
        return List.of();
    }

    @GetMapping("/ontology/sql-attributes")
    public List<SemanticDtos.SqlAttributeView> sqlAttributes(@RequestParam(required = false, name = "q") String query) {
        return ontology.sqlAttributes(query);
    }

    @GetMapping("/ontology/impact")
    public SemanticDtos.OntologyImpact impact(@RequestParam String table,
                                              @RequestParam(required = false) String column) {
        return ontology.impact(table, column);
    }

    @PostMapping("/ontology/joins")
    @ResponseStatus(HttpStatus.CREATED)
    public SemanticDtos.JoinView createJoin(@Valid @RequestBody SemanticDtos.JoinRequest request) {
        return ontology.createJoin(request);
    }

    @PostMapping("/ontology/columns/{id}/certify")
    public SemanticDtos.TermColumnView certifyColumn(@PathVariable UUID id,
                                                     @RequestBody(required = false) SemanticDtos.CertifyRequest request) {
        return ontology.certifyColumn(id, request);
    }

    @PostMapping("/ontology/attributes/{id}/certify")
    public SemanticDtos.SqlAttributeView certifyAttribute(@PathVariable UUID id,
                                                          @RequestBody(required = false) SemanticDtos.CertifyRequest request) {
        return ontology.certifyAttribute(id, request);
    }

    @PostMapping("/ontology/joins/{id}/certify")
    public SemanticDtos.JoinView certifyJoin(@PathVariable UUID id,
                                             @RequestBody(required = false) SemanticDtos.CertifyRequest request) {
        return ontology.certifyJoin(id, request);
    }
}
