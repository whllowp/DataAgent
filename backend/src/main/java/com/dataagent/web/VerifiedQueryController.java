package com.dataagent.web;

import com.dataagent.service.VerifiedQueryService;
import com.dataagent.web.dto.VerifiedQueryDtos;
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
@RequestMapping("/api/verified-queries")
public class VerifiedQueryController {

    private final VerifiedQueryService queries;

    public VerifiedQueryController(VerifiedQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public List<VerifiedQueryDtos.View> list(@RequestParam(required = false) String q) {
        return queries.list(q);
    }

    @GetMapping("/{id}")
    public VerifiedQueryDtos.View get(@PathVariable UUID id) {
        return queries.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VerifiedQueryDtos.View create(@Valid @RequestBody VerifiedQueryDtos.Request request) {
        return queries.create(request);
    }

    @PutMapping("/{id}")
    public VerifiedQueryDtos.View update(@PathVariable UUID id,
                                         @Valid @RequestBody VerifiedQueryDtos.Request request) {
        return queries.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        queries.delete(id);
    }

    @PostMapping("/{id}/validate")
    public VerifiedQueryDtos.ValidateResult validate(@PathVariable UUID id) {
        return queries.validate(id);
    }

    @PostMapping("/{id}/use")
    public VerifiedQueryDtos.View use(@PathVariable UUID id) {
        return queries.use(id);
    }
}
