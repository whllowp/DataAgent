package com.dataagent.web.dto;

import java.util.List;
import java.util.Map;

public final class DolphinSchedulerDtos {

    private DolphinSchedulerDtos() {
    }

    public record ImportRequest(
            String tenantId,
            Long projectCode,
            String projectName,
            List<ProcessDefinition> processDefinitions
    ) {
    }

    public record ProcessDefinition(
            Long code,
            String name,
            String description,
            String releaseState,
            Schedule schedule,
            List<TaskDefinition> taskDefinitions,
            List<TaskDefinition> taskDefinitionList,
            List<TaskRelation> taskRelations,
            List<TaskRelation> processTaskRelationList,
            List<WorkflowDependency> dependencies
    ) {
    }

    public record Schedule(
            String crontab,
            String scheduleCron,
            String timezoneId,
            String timezone,
            String releaseState
    ) {
    }

    public record TaskDefinition(
            Long code,
            String name,
            String taskType,
            String type,
            Map<String, Object> taskParams,
            String description,
            Integer taskOrder,
            String flag
    ) {
    }

    public record TaskRelation(
            Long preTaskCode,
            Long postTaskCode,
            String conditionType,
            Long lagSeconds
    ) {
    }

    public record WorkflowDependency(
            Long upstreamProcessCode,
            Long upstreamWorkflowCode,
            Long downstreamProcessCode,
            Long downstreamWorkflowCode,
            String conditionType,
            Long lagSeconds
    ) {
    }
}
