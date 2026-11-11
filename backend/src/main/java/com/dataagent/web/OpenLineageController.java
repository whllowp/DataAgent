package com.dataagent.web;

import com.dataagent.service.OpenLineageService;
import com.dataagent.web.dto.OpenLineageDtos;
import com.fasterxml.jackson.databind.JsonNode;
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

@RestController
@RequestMapping("/api")
public class OpenLineageController {

    private final OpenLineageService openLineage;

    public OpenLineageController(OpenLineageService openLineage) {
        this.openLineage = openLineage;
    }

    @PostMapping({"/v1/lineage", "/openlineage/events"})
    @ResponseStatus(HttpStatus.CREATED)
    public OpenLineageDtos.IngestResult ingest(@RequestBody JsonNode event) {
        return openLineage.ingest(event);
    }

    @GetMapping("/openlineage/events")
    public List<OpenLineageDtos.EventView> events(@RequestParam(required = false) String jobName) {
        return openLineage.events(jobName);
    }

    @GetMapping("/openlineage/jobs")
    public List<OpenLineageDtos.JobView> jobs(@RequestParam(required = false) String namespace) {
        return openLineage.jobs(namespace);
    }

    @GetMapping("/v1/namespaces")
    public List<OpenLineageDtos.NamespaceView> namespaces() {
        return openLineage.namespaces();
    }

    @GetMapping("/v1/namespaces/{namespace}/jobs")
    public java.util.Map<String, Object> namespacedJobs(@PathVariable String namespace) {
        List<OpenLineageDtos.JobView> jobs = openLineage.jobs(namespace);
        return java.util.Map.of("items", jobs, "totalCount", jobs.size());
    }

    @GetMapping("/v1/namespaces/{namespace}/jobs/{job}")
    public OpenLineageDtos.JobDetail job(@PathVariable String namespace, @PathVariable String job) {
        return openLineage.job(namespace, job);
    }

    @GetMapping("/v1/namespaces/{namespace}/jobs/{job}/runs")
    public java.util.Map<String, Object> runs(@PathVariable String namespace, @PathVariable String job) {
        List<OpenLineageDtos.RunView> runs = openLineage.runs(namespace, job);
        return java.util.Map.of("items", runs, "totalCount", runs.size());
    }

    @GetMapping("/v1/namespaces/{namespace}/jobs/{job}/runs/{runId}")
    public OpenLineageDtos.RunView run(@PathVariable String namespace, @PathVariable String job,
                                       @PathVariable String runId) {
        return openLineage.run(namespace, job, runId);
    }
}
