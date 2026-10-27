package com.dataagent.web;

import com.dataagent.service.MetadataService;
import com.dataagent.web.dto.MetadataDtos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/datasources/{datasourceId}")
public class MetadataController {

    private final MetadataService metadata;

    public MetadataController(MetadataService metadata) {
        this.metadata = metadata;
    }

    @GetMapping("/schemas")
    public MetadataDtos.SchemaList schemas(@PathVariable UUID datasourceId) {
        return new MetadataDtos.SchemaList(metadata.listSchemas(datasourceId));
    }

    @GetMapping("/tables")
    public MetadataDtos.TableList tables(@PathVariable UUID datasourceId,
                                         @RequestParam(required = false) String schema,
                                         @RequestParam(required = false, name = "q") String query) {
        return new MetadataDtos.TableList(metadata.listTables(datasourceId, schema, query));
    }

    @GetMapping("/tables/{schema}/{table}")
    public MetadataDtos.TableDetail table(@PathVariable UUID datasourceId,
                                          @PathVariable String schema,
                                          @PathVariable String table) {
        return metadata.describeTable(datasourceId, schema, table);
    }
}
