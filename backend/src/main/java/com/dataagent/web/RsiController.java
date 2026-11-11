package com.dataagent.web;

import com.dataagent.service.RsiService;
import com.dataagent.web.dto.RsiDtos;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/rsi")
public class RsiController {

    private final RsiService rsi;

    public RsiController(RsiService rsi) {
        this.rsi = rsi;
    }

    @GetMapping("/overview")
    public RsiDtos.Overview overview() {
        return rsi.overview();
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public RsiDtos.RunDetail start(@RequestBody(required = false) RsiDtos.StartRequest request) {
        return rsi.start(request);
    }

    @GetMapping("/runs/{id}")
    public RsiDtos.RunDetail run(@PathVariable UUID id) {
        return rsi.run(id);
    }

    @GetMapping("/runs/{id}/pareto")
    public List<RsiDtos.CandidateView> pareto(@PathVariable UUID id) {
        return rsi.pareto(id);
    }
}
