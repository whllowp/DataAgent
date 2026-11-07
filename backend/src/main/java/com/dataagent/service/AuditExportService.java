package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.domain.AuditEvent;
import com.dataagent.repository.AuditEventRepository;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.AuditDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

@Service
public class AuditExportService {

    private static final Logger log = LoggerFactory.getLogger(AuditExportService.class);
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC);

    private final AuditEventRepository events;
    private final DataAgentProperties properties;
    private final ObjectMapper objectMapper;

    public AuditExportService(AuditEventRepository events, DataAgentProperties properties,
                              ObjectMapper objectMapper) {
        this.events = events;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AuditDtos.ExportResult export(AuditDtos.ExportRequest request) {
        Instant to = request == null || request.to() == null ? Instant.now() : request.to();
        Instant from = request == null || request.from() == null
                ? to.minus(7, ChronoUnit.DAYS) : request.from();
        String jsonl = jsonl(from, to);
        List<AuditEvent> rows = events.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to);
        String name = "audit-" + FILE_TIME.format(Instant.now()) + ".jsonl";
        String checksum = sha256(jsonl);
        String destination = request == null || request.destination() == null
                ? "FILE" : request.destination().toUpperCase();
        String status = switch (destination) {
            case "SIEM" -> push(properties.getAudit().getSiemWebhook(), jsonl,
                    "SIEM webhook 未配置");
            case "OBJECT_STORE" -> putObjectStore(name, jsonl);
            default -> writeFile(name, jsonl);
        };
        return new AuditDtos.ExportResult(destination, name, rows.size(),
                jsonl.getBytes(StandardCharsets.UTF_8).length, checksum, status);
    }

    public String jsonl(Instant from, Instant to) {
        List<AuditEvent> rows = events.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to);
        StringBuilder builder = new StringBuilder();
        for (AuditEvent event : rows) {
            try {
                builder.append(objectMapper.writeValueAsString(view(event))).append('\n');
            } catch (Exception e) {
                throw new IllegalStateException("审计序列化失败", e);
            }
        }
        return builder.toString();
    }

    @Transactional
    public long purgeExpired() {
        int days = Math.max(1, properties.getAudit().getRetentionDays());
        long removed = events.deleteByCreatedAtBefore(Instant.now().minus(days, ChronoUnit.DAYS));
        if (removed > 0) {
            log.info("Purged {} audit events older than {} days", removed, days);
        }
        return removed;
    }

    private String writeFile(String name, String jsonl) {
        try {
            Path dir = Path.of(properties.getAudit().getExportDir());
            Files.createDirectories(dir);
            Path file = dir.resolve(name);
            Files.writeString(file, jsonl, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return file.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            throw ApiException.badRequest("审计导出写文件失败: " + e.getMessage());
        }
    }

    private String push(String webhook, String jsonl, String missingMessage) {
        if (webhook == null || webhook.isBlank()) {
            throw ApiException.badRequest(missingMessage);
        }
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(webhook))
                            .header("Content-Type", "application/x-ndjson")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonl))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw ApiException.badGateway("SIEM 返回 " + response.statusCode(), null);
            }
            return "SIEM " + response.statusCode();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badGateway("SIEM 推送失败: " + e.getMessage(), e);
        }
    }

    private String putObjectStore(String name, String jsonl) {
        String url = properties.getAudit().getObjectStoreUrl();
        if (url == null || url.isBlank()) {
            throw ApiException.badRequest("对象存储 URL 未配置");
        }
        try {
            String target = url.endsWith("/") ? url + name : url + "/" + name;
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target))
                    .header("Content-Type", "application/x-ndjson")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonl));
            String token = properties.getAudit().getObjectStoreToken();
            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw ApiException.badGateway("对象存储返回 " + response.statusCode(), null);
            }
            return "OBJECT_STORE " + response.statusCode() + " " + target;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badGateway("对象存储写入失败: " + e.getMessage(), e);
        }
    }

    private AuditDtos.View view(AuditEvent event) {
        return new AuditDtos.View(event.getId(), event.getActor(), event.getAction(),
                event.getResourceType(), event.getResourceId(), event.getDatasourceId(),
                event.getStatus(), event.getDetail(), event.getClientIp(), event.getUserAgent(),
                event.getCreatedAt());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
