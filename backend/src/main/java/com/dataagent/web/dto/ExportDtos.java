package com.dataagent.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExportDtos {

    private ExportDtos() {
    }

    public record ExportRequest(
            @NotNull UUID datasourceId,
            @NotBlank @Size(max = 100000) String sql,
            String format,
            @Min(1) @Max(5000) Integer maxRows,
            Boolean allowRisky
    ) {
    }

    public record ExportView(
            UUID id,
            UUID datasourceId,
            String sqlText,
            String format,
            String fileName,
            int rowCount,
            long byteSize,
            String status,
            String errorMessage,
            String createdBy,
            Instant createdAt,
            String downloadUrl
    ) {
    }

    public record ExportList(List<ExportView> items, int total) {
    }
}
