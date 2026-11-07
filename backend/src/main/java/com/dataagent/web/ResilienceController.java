package com.dataagent.web;

import com.dataagent.service.DatasourceResilience;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ops/resilience")
public class ResilienceController {

    private final DatasourceResilience resilience;

    public ResilienceController(DatasourceResilience resilience) {
        this.resilience = resilience;
    }

    @GetMapping
    public List<DatasourceResilience.StateView> states() {
        return resilience.states();
    }
}
