package com.dataagent.web;

import com.dataagent.service.MemoryService;
import com.dataagent.web.dto.MemoryDtos;
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
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memories;

    public MemoryController(MemoryService memories) {
        this.memories = memories;
    }

    @GetMapping
    public List<MemoryDtos.MemoryView> list(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String status) {
        return memories.list(query, scope, kind, status);
    }

    @GetMapping("/overview")
    public MemoryDtos.MemoryOverview overview() {
        return memories.overview();
    }

    @GetMapping("/{id}")
    public MemoryDtos.MemoryView get(@PathVariable UUID id) {
        return memories.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryDtos.CaptureResult capture(@Valid @RequestBody MemoryDtos.CaptureRequest request) {
        return memories.capture(request);
    }

    @PostMapping("/capture")
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryDtos.CaptureResult captureAlias(@Valid @RequestBody MemoryDtos.CaptureRequest request) {
        return memories.capture(request);
    }

    @PutMapping("/{id}")
    public MemoryDtos.MemoryView update(@PathVariable UUID id,
                                        @Valid @RequestBody MemoryDtos.CaptureRequest request) {
        return memories.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        memories.delete(id);
    }

    @PostMapping("/{id}/use")
    public MemoryDtos.MemoryView use(@PathVariable UUID id) {
        return memories.use(id);
    }

    @PostMapping("/{id}/archive")
    public MemoryDtos.MemoryView archive(@PathVariable UUID id) {
        return memories.archive(id);
    }

    @PostMapping("/archive-expired")
    public MemoryDtos.ArchiveResult archiveExpired() {
        return memories.archiveExpired();
    }
}
