package com.dataagent.web;

import com.dataagent.service.BenchmarkService;
import com.dataagent.web.dto.BenchmarkDtos;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/benchmarks")
public class BenchmarkController {

    private final BenchmarkService benchmarks;

    public BenchmarkController(BenchmarkService benchmarks) {
        this.benchmarks = benchmarks;
    }

    @GetMapping
    public List<BenchmarkDtos.View> list() {
        return benchmarks.list();
    }

    @GetMapping("/{id}")
    public BenchmarkDtos.View get(@PathVariable UUID id) {
        return benchmarks.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BenchmarkDtos.View create(@Valid @RequestBody BenchmarkDtos.Request request) {
        return benchmarks.create(request);
    }

    @PutMapping("/{id}")
    public BenchmarkDtos.View update(@PathVariable UUID id,
                                     @Valid @RequestBody BenchmarkDtos.Request request) {
        return benchmarks.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        benchmarks.delete(id);
    }

    @PostMapping("/{id}/run")
    public BenchmarkDtos.RunResult run(@PathVariable UUID id) {
        return benchmarks.run(id);
    }

    @PostMapping("/run")
    public BenchmarkDtos.RunAllResult runAll() {
        return benchmarks.runAll();
    }
}
