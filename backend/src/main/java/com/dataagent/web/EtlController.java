package com.dataagent.web;

import com.dataagent.service.EtlService;
import com.dataagent.service.LineageMaintenanceService;
import com.dataagent.web.dto.DolphinSchedulerDtos;
import com.dataagent.web.dto.EtlDtos;
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
@RequestMapping("/api/etl")
public class EtlController {

    private final EtlService etl;
    private final LineageMaintenanceService lineage;

    public EtlController(EtlService etl, LineageMaintenanceService lineage) {
        this.etl = etl;
        this.lineage = lineage;
    }

    @GetMapping("/overview")
    public EtlDtos.Overview overview() {
        return etl.overview();
    }

    @GetMapping("/workflows")
    public List<EtlDtos.WorkflowView> workflows() {
        return etl.list();
    }

    @GetMapping("/workflows/{id}")
    public EtlDtos.WorkflowView workflow(@PathVariable UUID id) {
        return etl.get(id);
    }

    @GetMapping("/workflows/{id}/tasks")
    public List<EtlDtos.TaskView> tasks(@PathVariable UUID id) {
        return etl.tasks(id);
    }

    @GetMapping("/tasks/{id}/fields")
    public List<EtlDtos.FieldLineage> taskFields(@PathVariable UUID id) {
        return lineage.taskFields(id);
    }

    @GetMapping("/field-lineage")
    public List<EtlDtos.FieldLineage> fieldLineage(@RequestParam String table,
                                                    @RequestParam(required = false) String column) {
        return lineage.fieldLineage(table, column);
    }

    @GetMapping("/workflows/{id}/task-dependencies")
    public List<EtlDtos.TaskDependencyView> taskDependencies(@PathVariable UUID id) {
        return etl.taskDependencies(id);
    }

    @GetMapping("/workflows/{id}/workflow-dependencies")
    public List<EtlDtos.WorkflowDependencyView> workflowDependencies(@PathVariable UUID id) {
        return etl.workflowDependencies(id);
    }

    @GetMapping("/workflows/{id}/graph")
    public EtlDtos.WorkflowGraph graph(@PathVariable UUID id) {
        return etl.graph(id);
    }

    @GetMapping("/workflows/{id}/schedule")
    public EtlDtos.ScheduleAnalysis schedule(@PathVariable UUID id) {
        return etl.schedule(id);
    }

    @GetMapping("/workflows/{id}/analysis")
    public EtlDtos.Analysis analysis(@PathVariable UUID id) {
        return etl.analysis(id);
    }

    @GetMapping("/lineage")
    public EtlDtos.TableLineage lineage(
            @RequestParam String table,
            @RequestParam(defaultValue = "downstream") String direction,
            @RequestParam(defaultValue = "10") int depth) {
        return etl.lineage(table, direction, depth);
    }

    @PostMapping("/lineage/rebuild")
    public java.util.Map<String, Integer> rebuildLineage() {
        int edges = lineage.rebuildTenant(
                com.dataagent.security.AuthContext.currentOrSystem().tenantId());
        return java.util.Map.of("edges", edges);
    }

    @PostMapping("/sync/dolphinscheduler")
    @ResponseStatus(HttpStatus.CREATED)
    public EtlDtos.ImportResult syncDolphinScheduler(
            @Valid @RequestBody DolphinSchedulerDtos.ImportRequest request) {
        return etl.importDolphinScheduler(request);
    }
}
