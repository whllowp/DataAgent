package com.dataagent.service;

import com.dataagent.domain.LineageEdge;
import com.dataagent.domain.LineageFieldEdge;
import com.dataagent.domain.OpenLineageEvent;
import com.dataagent.repository.LineageEdgeRepository;
import com.dataagent.repository.LineageFieldEdgeRepository;
import com.dataagent.repository.OpenLineageEventRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.web.dto.OpenLineageDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OpenLineageService {

    private final OpenLineageEventRepository events;
    private final LineageEdgeRepository edges;
    private final LineageFieldEdgeRepository fieldEdges;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public OpenLineageService(OpenLineageEventRepository events, LineageEdgeRepository edges,
                              LineageFieldEdgeRepository fieldEdges, ObjectMapper objectMapper,
                              AuditService audit) {
        this.events = events;
        this.edges = edges;
        this.fieldEdges = fieldEdges;
        this.objectMapper = objectMapper;
        this.audit = audit;
    }

    @Transactional
    public OpenLineageDtos.IngestResult ingest(JsonNode root) {
        String tenant = AuthContext.currentOrSystem().tenantId();
        String eventType = text(root.path("eventType"), "UNKNOWN").toUpperCase(Locale.ROOT);
        Instant eventTime = instant(root.path("eventTime"));
        JsonNode job = root.path("job");
        JsonNode run = root.path("run");
        String jobNamespace = text(job.path("namespace"), null);
        String jobName = text(job.path("name"), "unnamed-job");
        String runId = text(run.path("runId"), null);
        String producer = text(root.path("producer"), null);
        String schemaUrl = text(root.path("schemaURL"), null);
        JsonNode inputs = root.path("inputs");
        JsonNode outputs = root.path("outputs");

        OpenLineageEvent event = new OpenLineageEvent();
        event.setTenantId(tenant);
        event.setEventTime(eventTime);
        event.setEventType(eventType);
        event.setJobNamespace(jobNamespace);
        event.setJobName(jobName);
        event.setRunId(runId);
        event.setProducer(producer);
        event.setSchemaUrl(schemaUrl);
        event.setInputsJson(json(inputs));
        event.setOutputsJson(json(outputs));
        event.setColumnLineageJson(json(columnLineage(outputs)));
        event.setRawEvent(json(root));
        events.save(event);

        if (runId != null) {
            edges.deleteByTenantIdAndKindAndJobNameAndRunId(tenant, "OPENLINEAGE", jobName, runId);
            fieldEdges.deleteByTenantIdAndKindAndJobNameAndRunId(tenant, "OPENLINEAGE", jobName, runId);
        }
        int tableCount = 0;
        int fieldCount = 0;
        List<String> inputNames = names(inputs);
        List<String> outputNames = names(outputs);
        for (String input : inputNames) {
            for (String output : outputNames) {
                LineageEdge edge = new LineageEdge();
                edge.setTenantId(tenant);
                edge.setSourceTable(input);
                edge.setTargetTable(output);
                edge.setKind("OPENLINEAGE");
                edge.setJobName(jobName);
                edge.setRunId(runId);
                edges.save(edge);
                tableCount++;
            }
        }
        for (JsonNode output : outputs) {
            String targetTable = datasetName(output);
            JsonNode fields = output.path("facets").path("columnLineage").path("fields");
            if (targetTable == null || !fields.isObject()) {
                continue;
            }
            var fieldIterator = fields.fields();
            while (fieldIterator.hasNext()) {
                var targetField = fieldIterator.next();
                String targetColumn = targetField.getKey().toUpperCase(Locale.ROOT);
                for (JsonNode source : targetField.getValue().path("inputFields")) {
                    String sourceTable = datasetName(source);
                    if (sourceTable == null) {
                        continue;
                    }
                    LineageFieldEdge field = new LineageFieldEdge();
                    field.setTenantId(tenant);
                    field.setSourceTable(sourceTable);
                    field.setSourceColumn(text(source.path("field"), "*").toUpperCase(Locale.ROOT));
                    field.setTargetTable(targetTable);
                    field.setTargetColumn(targetColumn);
                    field.setExpression(text(targetField.getValue().path("transformationType"), null));
                    field.setKind("OPENLINEAGE");
                    field.setJobName(jobName);
                    field.setRunId(runId);
                    fieldEdges.save(field);
                    fieldCount++;
                }
            }
        }
        audit.record("OPENLINEAGE_INGEST", "SUCCESS", "JOB", null, null,
                "job=" + jobName + " run=" + runId + " tableEdges=" + tableCount + " fieldEdges=" + fieldCount);
        return new OpenLineageDtos.IngestResult(view(event), tableCount, fieldCount);
    }

    public List<OpenLineageDtos.EventView> events(String jobName) {
        String tenant = AuthContext.currentOrSystem().tenantId();
        List<OpenLineageEvent> rows = jobName == null || jobName.isBlank()
                ? events.findTop100ByTenantIdOrderByCreatedAtDesc(tenant)
                : events.findTop100ByTenantIdAndJobNameOrderByCreatedAtDesc(tenant, jobName);
        return rows.stream().map(this::view).toList();
    }

    public List<OpenLineageDtos.JobView> jobs() {
        return jobs(null);
    }

    public List<OpenLineageDtos.JobView> jobs(String namespace) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, Instant> last = new LinkedHashMap<>();
        for (OpenLineageEvent event : events.findTop100ByTenantIdOrderByCreatedAtDesc(
                AuthContext.currentOrSystem().tenantId())) {
            if (namespace != null && !namespace.isBlank()
                    && !namespace.equalsIgnoreCase(event.getJobNamespace())) {
                continue;
            }
            String key = (event.getJobNamespace() == null ? "" : event.getJobNamespace()) + "|" + event.getJobName();
            counts.computeIfAbsent(key, ignored -> new long[1])[0]++;
            if (event.getEventTime() != null) {
                last.merge(key, event.getEventTime(), (left, right) -> left.isAfter(right) ? left : right);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|", 2);
                    return new OpenLineageDtos.JobView(parts[0], parts[1], entry.getValue()[0],
                            last.get(entry.getKey()));
                })
                .sorted(Comparator.comparing(OpenLineageDtos.JobView::lastEventAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<OpenLineageDtos.NamespaceView> namespaces() {
        Map<String, Long> eventCounts = new LinkedHashMap<>();
        Map<String, java.util.Set<String>> jobNames = new LinkedHashMap<>();
        for (OpenLineageEvent event : events.findTop100ByTenantIdOrderByCreatedAtDesc(
                AuthContext.currentOrSystem().tenantId())) {
            String namespace = event.getJobNamespace() == null ? "" : event.getJobNamespace();
            eventCounts.merge(namespace, 1L, Long::sum);
            jobNames.computeIfAbsent(namespace, ignored -> new java.util.LinkedHashSet<>())
                    .add(event.getJobName());
        }
        return eventCounts.entrySet().stream()
                .map(entry -> new OpenLineageDtos.NamespaceView(entry.getKey(),
                        jobNames.getOrDefault(entry.getKey(), java.util.Set.of()).size(), entry.getValue()))
                .toList();
    }

    public OpenLineageDtos.JobDetail job(String namespace, String jobName) {
        String tenant = AuthContext.currentOrSystem().tenantId();
        List<OpenLineageEvent> rows = events.findTop100ByTenantIdAndJobNameOrderByCreatedAtDesc(tenant, jobName)
                .stream().filter(event -> namespace == null || namespace.equalsIgnoreCase(event.getJobNamespace()))
                .toList();
        if (rows.isEmpty()) {
            throw com.dataagent.web.ApiException.notFound("OpenLineage job 不存在: " + jobName);
        }
        Instant created = rows.stream().map(OpenLineageEvent::getCreatedAt).min(Comparator.naturalOrder())
                .orElse(null);
        Instant updated = rows.stream().map(OpenLineageEvent::getCreatedAt).max(Comparator.naturalOrder())
                .orElse(null);
        return new OpenLineageDtos.JobDetail(namespace, jobName, created, updated, runViews(rows));
    }

    public List<OpenLineageDtos.RunView> runs(String namespace, String jobName) {
        String tenant = AuthContext.currentOrSystem().tenantId();
        return runViews(events.findTop100ByTenantIdAndJobNameOrderByCreatedAtDesc(tenant, jobName)
                .stream().filter(event -> namespace == null || namespace.equalsIgnoreCase(event.getJobNamespace()))
                .toList());
    }

    public OpenLineageDtos.RunView run(String namespace, String jobName, String runId) {
        List<OpenLineageEvent> rows = events.findByTenantIdAndJobNameAndRunIdOrderByCreatedAtDesc(
                AuthContext.currentOrSystem().tenantId(), jobName, runId);
        if (rows.isEmpty()) {
            throw com.dataagent.web.ApiException.notFound("OpenLineage run 不存在: " + runId);
        }
        return runView(rows.get(0));
    }

    private List<OpenLineageDtos.RunView> runViews(List<OpenLineageEvent> rows) {
        Map<String, OpenLineageEvent> latest = new LinkedHashMap<>();
        for (OpenLineageEvent event : rows) {
            if (event.getRunId() == null) {
                continue;
            }
            OpenLineageEvent current = latest.get(event.getRunId());
            if (current == null || (event.getCreatedAt() != null
                    && current.getCreatedAt() != null
                    && event.getCreatedAt().isAfter(current.getCreatedAt()))) {
                latest.put(event.getRunId(), event);
            }
        }
        return latest.values().stream().map(this::runView).toList();
    }

    private OpenLineageDtos.RunView runView(OpenLineageEvent event) {
        String tenant = AuthContext.currentOrSystem().tenantId();
        List<OpenLineageDtos.RunTableEdge> tableEdges = edges.findByTenantId(tenant).stream()
                .filter(edge -> "OPENLINEAGE".equals(edge.getKind())
                        && event.getRunId() != null && event.getRunId().equals(edge.getRunId())
                        && event.getJobName() != null && event.getJobName().equals(edge.getJobName()))
                .map(edge -> new OpenLineageDtos.RunTableEdge(edge.getSourceTable(), edge.getTargetTable()))
                .distinct().toList();
        List<OpenLineageDtos.RunFieldEdge> fieldRefs = fieldEdges.findByTenantId(tenant).stream()
                .filter(edge -> "OPENLINEAGE".equals(edge.getKind())
                        && event.getRunId() != null && event.getRunId().equals(edge.getRunId())
                        && event.getJobName() != null && event.getJobName().equals(edge.getJobName()))
                .map(edge -> new OpenLineageDtos.RunFieldEdge(edge.getSourceTable(), edge.getSourceColumn(),
                        edge.getTargetTable(), edge.getTargetColumn(), edge.getExpression()))
                .toList();
        return new OpenLineageDtos.RunView(event.getRunId(), event.getEventType(), event.getEventTime(),
                event.getInputsJson(), event.getOutputsJson(), event.getColumnLineageJson(),
                tableEdges, fieldRefs);
    }

    public JsonNode toJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> names(JsonNode datasets) {
        List<String> result = new ArrayList<>();
        for (JsonNode dataset : datasets) {
            String name = datasetName(dataset);
            if (name != null) {
                result.add(name);
            }
        }
        return result;
    }

    private JsonNode columnLineage(JsonNode outputs) {
        var array = objectMapper.createArrayNode();
        for (JsonNode output : outputs) {
            if (output.path("facets").path("columnLineage").isObject()) {
                array.add(output);
            }
        }
        return array;
    }

    private String datasetName(JsonNode node) {
        String name = text(node.path("name"), null);
        if (name == null) {
            return null;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        int dot = upper.lastIndexOf('.');
        return dot >= 0 ? upper.substring(dot + 1) : upper;
    }

    private OpenLineageDtos.EventView view(OpenLineageEvent event) {
        return new OpenLineageDtos.EventView(event.getId(), event.getEventTime(), event.getEventType(),
                event.getJobNamespace(), event.getJobName(), event.getRunId(), event.getProducer(),
                event.getInputsJson(), event.getOutputsJson(), event.getColumnLineageJson(),
                event.getCreatedAt());
    }

    private String json(JsonNode node) {
        try {
            return node == null || node.isMissingNode() ? "[]" : objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Instant instant(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return Instant.now();
        }
        try {
            return Instant.parse(node.asText());
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private static String text(JsonNode node, String fallback) {
        return node == null || node.isMissingNode() || node.asText().isBlank() ? fallback : node.asText();
    }
}
