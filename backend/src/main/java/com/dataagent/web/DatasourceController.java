package com.dataagent.web;

import com.dataagent.service.DatasourceService;
import com.dataagent.web.dto.DatasourceDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/datasources")
public class DatasourceController {

    private final DatasourceService datasources;

    public DatasourceController(DatasourceService datasources) {
        this.datasources = datasources;
    }

    @GetMapping
    public DatasourceDtos.DatasourceList list() {
        return new DatasourceDtos.DatasourceList(datasources.list());
    }

    @GetMapping("/{id}")
    public DatasourceDtos.DatasourceView get(@PathVariable UUID id) {
        return datasources.getView(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DatasourceDtos.DatasourceView create(@Valid @RequestBody DatasourceDtos.DatasourceRequest request) {
        return datasources.create(request);
    }

    @PutMapping("/{id}")
    public DatasourceDtos.DatasourceView update(@PathVariable UUID id,
                                                @Valid @RequestBody DatasourceDtos.DatasourceRequest request) {
        return datasources.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        datasources.delete(id);
    }

    @PostMapping("/{id}/test")
    public DatasourceDtos.TestResult test(@PathVariable UUID id) {
        return datasources.test(id);
    }
}
