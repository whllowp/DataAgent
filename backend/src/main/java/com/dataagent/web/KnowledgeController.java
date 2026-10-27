package com.dataagent.web;

import com.dataagent.security.ApiTokenFilter;
import com.dataagent.service.KnowledgeService;
import com.dataagent.web.dto.KnowledgeDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledge;

    public KnowledgeController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @GetMapping
    public KnowledgeDtos.KnowledgePage search(@RequestParam(required = false, name = "q") String query,
                                              @RequestParam(required = false) String dialect,
                                              @RequestParam(required = false) String tag,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return knowledge.search(query, dialect, tag, page, size);
    }

    @GetMapping("/{id}")
    public KnowledgeDtos.KnowledgeView get(@PathVariable UUID id) {
        return knowledge.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeDtos.KnowledgeView create(@Valid @RequestBody KnowledgeDtos.KnowledgeRequest request,
                                              @RequestAttribute(name = ApiTokenFilter.USER_ATTRIBUTE) String user) {
        return knowledge.create(request, user);
    }

    @PutMapping("/{id}")
    public KnowledgeDtos.KnowledgeView update(@PathVariable UUID id,
                                              @Valid @RequestBody KnowledgeDtos.KnowledgeRequest request,
                                              @RequestAttribute(name = ApiTokenFilter.USER_ATTRIBUTE) String user) {
        return knowledge.update(id, request, user);
    }

    @PostMapping("/{id}/use")
    public KnowledgeDtos.KnowledgeView use(@PathVariable UUID id) {
        return knowledge.markUsed(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        knowledge.delete(id);
    }
}
