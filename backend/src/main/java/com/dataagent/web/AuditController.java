package com.dataagent.web;

import com.dataagent.service.AuditExportService;
import com.dataagent.service.AuditService;
import com.dataagent.web.dto.AuditDtos;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService audit;
    private final AuditExportService export;

    public AuditController(AuditService audit, AuditExportService export) {
        this.audit = audit;
        this.export = export;
    }

    @GetMapping
    public List<AuditDtos.View> search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return audit.search(actor, action, resourceType, status, from, to, limit, offset);
    }

    @GetMapping("/overview")
    public AuditDtos.Overview overview() {
        return audit.overview();
    }

    @PostMapping("/export")
    public AuditDtos.ExportResult export(@RequestBody(required = false) AuditDtos.ExportRequest request) {
        return export.export(request);
    }

    @GetMapping("/export/download")
    public ResponseEntity<String> download(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minusSeconds(7 * 86400L) : from;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit.jsonl\"")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(export.jsonl(start, end));
    }

    @PostMapping("/purge")
    public long purge() {
        return export.purgeExpired();
    }
}
