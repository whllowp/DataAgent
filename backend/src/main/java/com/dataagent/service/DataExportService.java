package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.domain.DataExport;
import com.dataagent.repository.DataExportRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.sql.SqlGuard;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.ExportDtos;
import com.dataagent.web.dto.SqlDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Exports a read-only query result to a downloadable file (CSV / JSON / JSONL).
 * The rows are the already-PII-masked values returned by {@link JdbcQueryService},
 * so exports never leak more than what the query UI shows.
 */
@Service
public class DataExportService {

    private static final Logger log = LoggerFactory.getLogger(DataExportService.class);
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.systemDefault());

    private final JdbcQueryService queries;
    private final SqlGuard guard;
    private final DataExportRepository exports;
    private final DataAgentProperties properties;

    public DataExportService(JdbcQueryService queries, SqlGuard guard,
                             DataExportRepository exports, DataAgentProperties properties) {
        this.queries = queries;
        this.guard = guard;
        this.exports = exports;
        this.properties = properties;
    }

    @Transactional
    public ExportDtos.ExportView export(ExportDtos.ExportRequest request, String user) {
        SqlGuard.GuardedSql guarded = guard.guardForExecution(request.sql());
        String format = normalizeFormat(request.format());
        int hardMax = properties.getExport().getMaxExportRows();
        int maxRows = request.maxRows() == null ? hardMax : Math.min(request.maxRows(), hardMax);

        SqlDtos.QueryResult result = queries.execute(request.datasourceId(), guarded.sql(), maxRows, null,
                false, false, true, user, null, null, Boolean.TRUE.equals(request.allowRisky()));

        DataExport record = new DataExport();
        record.setTenantId(AuthContext.currentOrSystem().tenantId());
        record.setDatasourceId(request.datasourceId());
        record.setSqlText(guarded.sql());
        record.setSqlFingerprint(guarded.fingerprint());
        record.setFormat(format);
        record.setCreatedBy(user);
        record.setCreatedAt(Instant.now());

        if ("ERROR".equals(result.status())) {
            record.setStatus("ERROR");
            record.setErrorMessage(result.message());
            record.setRowCount(0);
            record.setFileName("export-error-" + System.currentTimeMillis() + ".txt");
            record.setFilePath("");
            exports.save(record);
            throw ApiException.badRequest("导出查询执行失败: " + result.message());
        }

        byte[] bytes = serialize(result, format);
        String extension = "jsonl".equals(format) ? "jsonl" : format;
        String fileName = "export-" + FILE_TS.format(record.getCreatedAt()) + "-"
                + suffix(guarded.fingerprint()) + "." + extension;

        Path dir = Path.of(properties.getExport().getExportDir());
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), bytes);
        } catch (IOException e) {
            record.setStatus("ERROR");
            record.setErrorMessage("写入导出文件失败: " + e.getMessage());
            exports.save(record);
            throw ApiException.serviceUnavailable("写入导出文件失败: " + e.getMessage());
        }

        record.setFileName(fileName);
        record.setFilePath(dir.resolve(fileName).toAbsolutePath().toString());
        record.setRowCount(result.rowCount());
        record.setByteSize(bytes.length);
        record.setStatus("SUCCESS");
        exports.save(record);
        log.info("Exported {} rows to {} ({}, {} bytes) for user {}",
                result.rowCount(), fileName, format, bytes.length, user);
        return view(record);
    }

    @Transactional(readOnly = true)
    public ExportDtos.ExportView get(UUID id) {
        return view(getRequired(id));
    }

    @Transactional(readOnly = true)
    public ExportDtos.ExportList list() {
        List<ExportDtos.ExportView> items = exports.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::view).toList();
        return new ExportDtos.ExportList(items, items.size());
    }

    @Transactional(readOnly = true)
    public DownloadedFile download(UUID id) {
        DataExport record = getRequired(id);
        if (!"SUCCESS".equals(record.getStatus()) || record.getFilePath() == null
                || record.getFilePath().isBlank()) {
            throw ApiException.notFound("导出文件不存在: " + id);
        }
        Path path = Path.of(record.getFilePath());
        if (!Files.exists(path)) {
            throw ApiException.notFound("导出文件已清理: " + record.getFileName());
        }
        try {
            return new DownloadedFile(record.getFileName(), contentType(record.getFormat()),
                    Files.readAllBytes(path));
        } catch (IOException e) {
            throw ApiException.serviceUnavailable("读取导出文件失败: " + e.getMessage());
        }
    }

    public record DownloadedFile(String fileName, String contentType, byte[] content) {
    }

    private DataExport getRequired(UUID id) {
        return exports.findById(id).orElseThrow(() -> ApiException.notFound("导出记录不存在: " + id));
    }

    private ExportDtos.ExportView view(DataExport record) {
        return new ExportDtos.ExportView(
                record.getId(), record.getDatasourceId(), record.getSqlText(), record.getFormat(),
                record.getFileName(), record.getRowCount(), record.getByteSize(), record.getStatus(),
                record.getErrorMessage(), record.getCreatedBy(), record.getCreatedAt(),
                "/api/export/" + record.getId() + "/download");
    }

    private String normalizeFormat(String format) {
        String value = format == null || format.isBlank() ? "csv" : format.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "csv", "json", "jsonl" -> value;
            default -> throw ApiException.badRequest("不支持的导出格式: " + format + "（可选 csv/json/jsonl）");
        };
    }

    public byte[] serialize(SqlDtos.QueryResult result, String format) {
        return switch (format) {
            case "json" -> serializeJson(result).getBytes(StandardCharsets.UTF_8);
            case "jsonl" -> serializeJsonl(result).getBytes(StandardCharsets.UTF_8);
            default -> serializeCsv(result).getBytes(StandardCharsets.UTF_8);
        };
    }

    private String serializeCsv(SqlDtos.QueryResult result) {
        StringBuilder sb = new StringBuilder(Math.max(64, result.rowCount() * 16));
        List<String> headers = result.columns().stream().map(this::header).toList();
        sb.append(csvLine(headers)).append('\n');
        for (List<Object> row : result.rows()) {
            sb.append(csvLine(row)).append('\n');
        }
        return sb.toString();
    }

    private String csvLine(List<?> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csvCell(cells.get(i)));
        }
        return sb.toString();
    }

    private String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value instanceof List<?> list ? joinList(list) : String.valueOf(value);
        boolean needsQuote = text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        if (!needsQuote) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private String serializeJson(SqlDtos.QueryResult result) {
        StringBuilder sb = new StringBuilder(Math.max(64, result.rowCount() * 24));
        sb.append('[');
        List<String> headers = result.columns().stream().map(this::header).toList();
        for (int r = 0; r < result.rows().size(); r++) {
            if (r > 0) {
                sb.append(',');
            }
            appendJsonObject(sb, headers, result.rows().get(r));
        }
        sb.append(']');
        return sb.toString();
    }

    private String serializeJsonl(SqlDtos.QueryResult result) {
        StringBuilder sb = new StringBuilder(Math.max(64, result.rowCount() * 24));
        List<String> headers = result.columns().stream().map(this::header).toList();
        for (List<Object> row : result.rows()) {
            appendJsonObject(sb, headers, row);
            sb.append('\n');
        }
        return sb.toString();
    }

    private void appendJsonObject(StringBuilder sb, List<String> headers, List<Object> row) {
        sb.append('{');
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(jsonEscape(headers.get(i))).append("\":")
                    .append(jsonValue(i < row.size() ? row.get(i) : null));
        }
        sb.append('}');
    }

    private String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean b) {
            return b.toString();
        }
        if (value instanceof Number n) {
            return n.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(jsonValue(list.get(i)));
            }
            return sb.append(']').toString();
        }
        return '"' + jsonEscape(String.valueOf(value)) + '"';
    }

    private String jsonEscape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private String header(SqlDtos.ColumnMeta column) {
        return column.label() != null && !column.label().isBlank() ? column.label() : column.name();
    }

    private String joinList(List<?> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private String suffix(String fingerprint) {
        if (fingerprint == null || fingerprint.length() < 8) {
            return "00000000";
        }
        return fingerprint.substring(0, 8);
    }

    private String contentType(String format) {
        return switch (format) {
            case "json", "jsonl" -> "application/json; charset=UTF-8";
            default -> "text/csv; charset=UTF-8";
        };
    }
}
