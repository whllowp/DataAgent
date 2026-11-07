package com.dataagent.web;

import com.dataagent.service.LineageService;
import com.dataagent.web.dto.LineageDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lineage")
public class LineageController {

    private final LineageService lineage;

    public LineageController(LineageService lineage) {
        this.lineage = lineage;
    }

    @GetMapping("/tables")
    public List<LineageDtos.TableSummary> tables() {
        return lineage.tables();
    }

    @GetMapping("/impact")
    public LineageDtos.Impact impact(@RequestParam String table) {
        return lineage.impact(table);
    }

    @GetMapping("/fields")
    public List<LineageDtos.FieldSummary> fields() {
        return lineage.fields();
    }

    @GetMapping("/field-impact")
    public LineageDtos.FieldImpact fieldImpact(@RequestParam String table, @RequestParam String column) {
        return lineage.fieldImpact(table, column);
    }
}
