package com.dataagent.web;

import com.dataagent.service.OpenLineageService;
import com.dataagent.web.dto.OpenLineageDtos;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
    public List<OpenLineageDtos.JobView> jobs() {
        return openLineage.jobs();
    }
}
