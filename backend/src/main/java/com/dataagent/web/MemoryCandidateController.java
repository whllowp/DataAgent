package com.dataagent.web;

import com.dataagent.service.MemoryCandidateService;
import com.dataagent.web.dto.MemoryCandidateDtos;
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
@RequestMapping("/api/memory/candidates")
public class MemoryCandidateController {

    private final MemoryCandidateService candidates;

    public MemoryCandidateController(MemoryCandidateService candidates) {
        this.candidates = candidates;
    }

    @GetMapping
    public List<MemoryCandidateDtos.View> list(@RequestParam(required = false) String status) {
        return candidates.list(status);
    }

    @PostMapping("/extract")
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryCandidateDtos.ExtractResult extract(
            @Valid @RequestBody MemoryCandidateDtos.ExtractRequest request) {
        return candidates.extract(request);
    }

    @PostMapping("/{id}/approve")
    public MemoryCandidateDtos.ApproveResult approve(
            @PathVariable UUID id,
            @RequestBody(required = false) MemoryCandidateDtos.ApproveRequest request) {
        return candidates.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    public MemoryCandidateDtos.RejectResult reject(
            @PathVariable UUID id,
            @RequestBody(required = false) MemoryCandidateDtos.RejectRequest request) {
        return candidates.reject(id, request);
    }
}
