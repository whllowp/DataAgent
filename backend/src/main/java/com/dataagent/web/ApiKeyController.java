package com.dataagent.web;

import com.dataagent.service.ApiKeyService;
import com.dataagent.web.dto.ApiKeyDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiKeyController {

    private final ApiKeyService apiKeys;

    public ApiKeyController(ApiKeyService apiKeys) {
        this.apiKeys = apiKeys;
    }

    @GetMapping("/auth/me")
    public ApiKeyDtos.Me me() {
        return apiKeys.me();
    }

    @GetMapping("/api-keys")
    public List<ApiKeyDtos.View> list() {
        return apiKeys.list();
    }

    @PostMapping("/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyDtos.Created create(@Valid @RequestBody ApiKeyDtos.CreateRequest request) {
        return apiKeys.create(request);
    }

    @DeleteMapping("/api-keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        apiKeys.revoke(id);
    }
}
