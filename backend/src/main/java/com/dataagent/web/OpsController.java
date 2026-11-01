package com.dataagent.web;

import com.dataagent.service.OpsService;
import com.dataagent.web.dto.OpsDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private final OpsService ops;

    public OpsController(OpsService ops) {
        this.ops = ops;
    }

    @GetMapping("/overview")
    public OpsDtos.OpsOverview overview() {
        return ops.overview();
    }
}
