package com.dataagent.web;

import com.dataagent.security.ApiTokenFilter;
import com.dataagent.service.DataExportService;
import com.dataagent.web.dto.ExportDtos;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/export")
public class DataExportController {

    private final DataExportService exports;

    public DataExportController(DataExportService exports) {
        this.exports = exports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExportDtos.ExportView export(@Valid @RequestBody ExportDtos.ExportRequest request,
                                        @RequestAttribute(name = ApiTokenFilter.USER_ATTRIBUTE) String user) {
        return exports.export(request, user);
    }

    @GetMapping
    public ExportDtos.ExportList list() {
        return exports.list();
    }

    @GetMapping("/{id}")
    public ExportDtos.ExportView get(@PathVariable UUID id) {
        return exports.get(id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        DataExportService.DownloadedFile file = exports.download(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(file.content(), headers, HttpStatus.OK);
    }
}
