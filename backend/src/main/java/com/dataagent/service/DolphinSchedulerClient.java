package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.DolphinSchedulerDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal DolphinScheduler 3.x REST adapter. It reads the process definition
 * list, then attempts the view endpoint to enrich each definition with tasks
 * and task relations. The exact deployment path is configurable via base-url;
 * the adapter accepts both normalized and standard DS JSON shapes.
 */
@Service
public class DolphinSchedulerClient {

    private final DataAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DolphinSchedulerClient(DataAgentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public DolphinSchedulerDtos.ImportRequest pull(Long projectCode) {
        String baseUrl = properties.getDolphinscheduler().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw ApiException.badRequest("DolphinScheduler base-url 未配置");
        }
        long project = projectCode != null ? projectCode
                : properties.getDolphinscheduler().getProjectCode() == null
                ? 0L : properties.getDolphinscheduler().getProjectCode();
        if (project <= 0) {
            throw ApiException.badRequest("DolphinScheduler projectCode 未配置");
        }
        String root = baseUrl.replaceAll("/+$", "");
        if (root.endsWith("/dolphinscheduler")) {
            root = root.substring(0, root.length() - "/dolphinscheduler".length());
        }
        String listUrl = root + "/dolphinscheduler/projects/" + project
                + "/process-definition?pageNo=1&pageSize=200";
        JsonNode response = get(listUrl);
        JsonNode data = response.path("data");
        JsonNode list = data.path("totalList");
        if (!list.isArray()) {
            list = data.isArray() ? data : response.isArray() ? response : null;
        }
        if (list == null || !list.isArray()) {
            throw ApiException.badGateway("DolphinScheduler 返回格式不包含 process definition 列表", null);
        }
        List<DolphinSchedulerDtos.ProcessDefinition> definitions = new ArrayList<>();
        for (JsonNode item : list) {
            JsonNode definitionNode = item.has("processDefinition")
                    ? item.path("processDefinition") : item;
            DolphinSchedulerDtos.ProcessDefinition definition = convert(definitionNode);
            if (definition == null) {
                continue;
            }
            if (definition.taskDefinitions() == null && definition.taskDefinitionList() == null
                    && definition.code() != null) {
                DolphinSchedulerDtos.ProcessDefinition detail = view(root, project, definition.code());
                if (detail != null) {
                    definition = merge(definition, detail);
                }
            }
            definitions.add(definition);
        }
        return new DolphinSchedulerDtos.ImportRequest("default", project,
                properties.getDolphinscheduler().getProjectName(), definitions);
    }

    private DolphinSchedulerDtos.ProcessDefinition view(String root, long project, long code) {
        String[] paths = {
                "/dolphinscheduler/projects/" + project + "/process-definition/" + code + "/view",
                "/dolphinscheduler/projects/" + project + "/process-definition/" + code
        };
        for (String path : paths) {
            try {
                JsonNode response = get(root + path);
                JsonNode data = response.has("data") ? response.path("data") : response;
                JsonNode node = data.has("processDefinition") ? data.path("processDefinition") : data;
                DolphinSchedulerDtos.ProcessDefinition definition = convert(node);
                if (definition != null) {
                    return definition;
                }
            } catch (Exception ignored) {
                // try the next DS endpoint shape
            }
        }
        return null;
    }

    private DolphinSchedulerDtos.ProcessDefinition merge(DolphinSchedulerDtos.ProcessDefinition base,
                                                         DolphinSchedulerDtos.ProcessDefinition detail) {
        return new DolphinSchedulerDtos.ProcessDefinition(
                base.code() == null ? detail.code() : base.code(),
                base.name() == null ? detail.name() : base.name(),
                base.description() == null ? detail.description() : base.description(),
                base.releaseState() == null ? detail.releaseState() : base.releaseState(),
                detail.schedule() == null ? base.schedule() : detail.schedule(),
                detail.taskDefinitions() == null ? base.taskDefinitions() : detail.taskDefinitions(),
                detail.taskDefinitionList() == null ? base.taskDefinitionList() : detail.taskDefinitionList(),
                detail.taskRelations() == null ? base.taskRelations() : detail.taskRelations(),
                detail.processTaskRelationList() == null ? base.processTaskRelationList() : detail.processTaskRelationList(),
                detail.dependencies() == null ? base.dependencies() : detail.dependencies());
    }

    private DolphinSchedulerDtos.ProcessDefinition convert(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.treeToValue(node, DolphinSchedulerDtos.ProcessDefinition.class);
        } catch (Exception error) {
            return null;
        }
    }

    private JsonNode get(String url) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET();
            String token = properties.getDolphinscheduler().getToken();
            if (token != null && !token.isBlank()) {
                builder.header("token", token);
                builder.header("Authorization", token);
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw ApiException.badGateway("DolphinScheduler HTTP " + response.statusCode(), null);
            }
            return objectMapper.readTree(response.body());
        } catch (ApiException error) {
            throw error;
        } catch (Exception error) {
            throw ApiException.badGateway("DolphinScheduler 请求失败: " + error.getMessage(), error);
        }
    }
}
