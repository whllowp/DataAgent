package com.dataagent.service;

import com.dataagent.domain.EtlTask;
import com.dataagent.domain.EtlTaskDependency;
import com.dataagent.domain.EtlWorkflow;
import com.dataagent.domain.EtlWorkflowDependency;
import com.dataagent.repository.EtlTaskDependencyRepository;
import com.dataagent.repository.EtlTaskRepository;
import com.dataagent.repository.EtlWorkflowDependencyRepository;
import com.dataagent.repository.EtlWorkflowRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.DolphinSchedulerDtos;
import com.dataagent.web.dto.EtlDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EtlService {

    private final EtlWorkflowRepository workflows;
    private final EtlTaskRepository tasks;
    private final EtlTaskDependencyRepository taskDependencies;
    private final EtlWorkflowDependencyRepository workflowDependencies;
    private final LineageService lineage;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public EtlService(EtlWorkflowRepository workflows, EtlTaskRepository tasks,
                      EtlTaskDependencyRepository taskDependencies,
                      EtlWorkflowDependencyRepository workflowDependencies,
                      LineageService lineage, AuditService audit, ObjectMapper objectMapper) {
        this.workflows = workflows;
        this.tasks = tasks;
        this.taskDependencies = taskDependencies;
        this.workflowDependencies = workflowDependencies;
        this.lineage = lineage;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<EtlDtos.WorkflowView> list() {
        String tenant = AuthContext.currentOrSystem().tenantId();
        List<EtlWorkflow> items = workflows.findByTenantIdOrderByNameAsc(tenant);
        List<EtlWorkflowDependency> deps = workflowDependencies.findAll();
        return items.stream().map(workflow -> view(workflow, deps)).toList();
    }

    @Transactional(readOnly = true)
    public EtlDtos.WorkflowView get(UUID id) {
        EtlWorkflow workflow = required(id);
        return view(workflow, workflowDependencies.findAll());
    }

    @Transactional(readOnly = true)
    public List<EtlDtos.TaskView> tasks(UUID workflowId) {
        required(workflowId);
        return taskViews(workflowId);
    }

    @Transactional(readOnly = true)
    public List<EtlDtos.TaskDependencyView> taskDependencies(UUID workflowId) {
        required(workflowId);
        return taskDependencies.findByWorkflowId(workflowId).stream()
                .map(dep -> new EtlDtos.TaskDependencyView(dep.getId(), dep.getWorkflowId(),
                        dep.getUpstreamTaskId(), dep.getDownstreamTaskId(),
                        dep.getConditionType(), dep.getLagSeconds()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EtlDtos.WorkflowDependencyView> workflowDependencies(UUID workflowId) {
        required(workflowId);
        Map<UUID, EtlWorkflow> byId = workflows.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(EtlWorkflow::getId, item -> item));
        return workflowDependencies.findByUpstreamWorkflowIdOrDownstreamWorkflowId(workflowId, workflowId)
                .stream().map(dep -> new EtlDtos.WorkflowDependencyView(
                        dep.getId(), dep.getUpstreamWorkflowId(),
                        byId.containsKey(dep.getUpstreamWorkflowId()) ? byId.get(dep.getUpstreamWorkflowId()).getName() : null,
                        dep.getDownstreamWorkflowId(),
                        byId.containsKey(dep.getDownstreamWorkflowId()) ? byId.get(dep.getDownstreamWorkflowId()).getName() : null,
                        dep.getConditionType(), dep.getLagSeconds()))
                .toList();
    }

    @Transactional(readOnly = true)
    public EtlDtos.WorkflowGraph graph(UUID workflowId) {
        EtlWorkflow workflow = required(workflowId);
        List<EtlTask> items = tasks.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        List<EtlTaskDependency> deps = taskDependencies.findByWorkflowId(workflowId);
        Topology topology = topology(items, deps);
        List<EtlDtos.GraphNode> nodes = items.stream()
                .sorted(Comparator.comparingInt(EtlTask::getOrderIndex))
                .map(task -> new EtlDtos.GraphNode(task.getId(), task.getName(), task.getTaskType(), "READY"))
                .toList();
        List<EtlDtos.GraphEdge> edges = deps.stream()
                .map(dep -> new EtlDtos.GraphEdge(dep.getUpstreamTaskId(), dep.getDownstreamTaskId(),
                        dep.getConditionType(), dep.getLagSeconds()))
                .toList();
        return new EtlDtos.WorkflowGraph(workflow.getId(), workflow.getName(), workflow.getScheduleCron(),
                workflow.getTimezone(), nextRun(workflow), nodes, edges, topology.order,
                topology.cycles, topology.orphans, sourceTables(items), targetTables(items));
    }

    @Transactional(readOnly = true)
    public EtlDtos.ScheduleAnalysis schedule(UUID workflowId) {
        EtlWorkflow workflow = required(workflowId);
        List<EtlTask> items = tasks.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        Topology topology = topology(items, taskDependencies.findByWorkflowId(workflowId));
        CriticalPath critical = criticalPath(items, topology);
        return new EtlDtos.ScheduleAnalysis(workflow.getScheduleCron(), workflow.getTimezone(),
                nextRun(workflow), items.size(), critical.length, critical.path, topology.order);
    }

    @Transactional(readOnly = true)
    public EtlDtos.Analysis analysis(UUID workflowId) {
        EtlWorkflow workflow = required(workflowId);
        List<EtlTask> items = tasks.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        Topology topology = topology(items, taskDependencies.findByWorkflowId(workflowId));
        CriticalPath critical = criticalPath(items, topology);
        List<EtlDtos.WorkflowDependencyChain> upstream = chains(workflowId, true);
        List<EtlDtos.WorkflowDependencyChain> downstream = chains(workflowId, false);
        Set<UUID> related = new LinkedHashSet<>();
        related.add(workflowId);
        upstream.forEach(chain -> related.add(chain.workflowId()));
        downstream.forEach(chain -> related.add(chain.workflowId()));
        List<EtlDtos.WorkflowView> relatedViews = workflows.findAllById(related).stream()
                .map(item -> view(item, workflowDependencies.findAll()))
                .toList();
        EtlDtos.ScheduleAnalysis schedule = new EtlDtos.ScheduleAnalysis(workflow.getScheduleCron(),
                workflow.getTimezone(), nextRun(workflow), items.size(), critical.length,
                critical.path, topology.order);
        return new EtlDtos.Analysis(workflow.getId(), workflow.getName(), schedule,
                sourceTables(items), targetTables(items), topology.orphans, topology.cycles,
                upstream, downstream, relatedViews);
    }

    @Transactional(readOnly = true)
    public EtlDtos.Overview overview() {
        List<EtlWorkflow> items = workflows.findByTenantIdOrderByNameAsc(AuthContext.currentOrSystem().tenantId());
        Set<UUID> ids = items.stream().map(EtlWorkflow::getId).collect(java.util.stream.Collectors.toSet());
        List<EtlTask> allTasks = ids.isEmpty() ? List.of() : tasks.findByWorkflowIdIn(new ArrayList<>(ids));
        List<EtlTaskDependency> deps = ids.isEmpty() ? List.of() : taskDependencies.findByWorkflowIdIn(new ArrayList<>(ids));
        Map<UUID, List<EtlTask>> byWorkflow = allTasks.stream()
                .collect(java.util.stream.Collectors.groupingBy(EtlTask::getWorkflowId));
        long orphan = items.stream().mapToLong(workflow -> {
            List<EtlTask> workflowTasks = byWorkflow.getOrDefault(workflow.getId(), List.of());
            if (workflowTasks.size() <= 1) {
                return 0;
            }
            Set<UUID> connected = new HashSet<>();
            deps.stream().filter(dep -> dep.getWorkflowId().equals(workflow.getId()))
                    .forEach(dep -> {
                        connected.add(dep.getUpstreamTaskId());
                        connected.add(dep.getDownstreamTaskId());
                    });
            return workflowTasks.stream().filter(task -> !connected.contains(task.getId())).count();
        }).sum();
        long cycles = items.stream().filter(workflow -> {
            List<EtlTask> workflowTasks = byWorkflow.getOrDefault(workflow.getId(), List.of());
            List<EtlTaskDependency> workflowDeps = deps.stream()
                    .filter(dep -> dep.getWorkflowId().equals(workflow.getId())).toList();
            return !topology(workflowTasks, workflowDeps).cycles.isEmpty();
        }).count();
        long scheduled = items.stream().filter(item -> notBlank(item.getScheduleCron())).count();
        List<EtlDtos.WorkflowView> recent = items.stream()
                .sorted(Comparator.comparing(EtlWorkflow::getUpdatedAt).reversed())
                .limit(8)
                .map(item -> view(item, workflowDependencies.findAll()))
                .toList();
        return new EtlDtos.Overview(items.size(), allTasks.size(), scheduled,
                items.size() - scheduled, cycles, orphan, recent);
    }

    @Transactional(readOnly = true)
    public EtlDtos.TableLineage lineage(String table, String direction, int maxDepth) {
        String start = canonical(table);
        if (start.isBlank()) {
            throw ApiException.badRequest("table 不能为空");
        }
        String dir = direction == null || direction.isBlank() ? "downstream"
                : direction.trim().toLowerCase(Locale.ROOT);
        if (!"downstream".equals(dir) && !"upstream".equals(dir)) {
            throw ApiException.badRequest("direction 必须是 upstream 或 downstream");
        }
        int depthLimit = Math.max(1, Math.min(maxDepth <= 0 ? 10 : maxDepth, 30));
        String tenant = AuthContext.currentOrSystem().tenantId();
        List<EtlWorkflow> workflowItems = workflows.findByTenantIdOrderByNameAsc(tenant);
        Map<UUID, EtlWorkflow> workflowById = workflowItems.stream()
                .collect(java.util.stream.Collectors.toMap(EtlWorkflow::getId, item -> item));
        List<EtlTask> allTasks = workflowItems.isEmpty() ? List.of()
                : tasks.findByWorkflowIdIn(workflowItems.stream().map(EtlWorkflow::getId).toList());
        Map<String, List<EtlTaskEdge>> bySource = new HashMap<>();
        Map<String, List<EtlTaskEdge>> byTarget = new HashMap<>();
        for (EtlTask task : allTasks) {
            EtlWorkflow workflow = workflowById.get(task.getWorkflowId());
            if (workflow == null || !workflow.isEnabled() || !task.isEnabled()) {
                continue;
            }
            String target = target(task);
            if (target == null) {
                continue;
            }
            for (String source : sources(task)) {
                EtlTaskEdge edge = new EtlTaskEdge(source, target, task, workflow);
                bySource.computeIfAbsent(source, ignored -> new ArrayList<>()).add(edge);
                byTarget.computeIfAbsent(target, ignored -> new ArrayList<>()).add(edge);
            }
        }
        Map<String, Integer> nodes = new LinkedHashMap<>();
        List<EtlDtos.LineageEdge> edges = new ArrayList<>();
        Set<UUID> relatedWorkflows = new LinkedHashSet<>();
        Deque<TableDepth> queue = new ArrayDeque<>();
        nodes.put(start, 0);
        queue.add(new TableDepth(start, 0));
        while (!queue.isEmpty()) {
            TableDepth current = queue.removeFirst();
            if (current.depth() >= depthLimit) {
                continue;
            }
            List<EtlTaskEdge> next = "downstream".equals(dir)
                    ? bySource.getOrDefault(current.table(), List.of())
                    : byTarget.getOrDefault(current.table(), List.of());
            for (EtlTaskEdge edge : next) {
                String neighbor = "downstream".equals(dir) ? edge.target() : edge.source();
                int nextDepth = current.depth() + 1;
                nodes.putIfAbsent(neighbor, nextDepth);
                edges.add(new EtlDtos.LineageEdge(edge.source(), edge.target(), edge.task().getId(),
                        edge.task().getName(), edge.workflow().getId(), edge.workflow().getName(),
                        edge.workflow().getScheduleCron(), nextDepth));
                relatedWorkflows.add(edge.workflow().getId());
                if (nodes.get(neighbor) == nextDepth) {
                    queue.add(new TableDepth(neighbor, nextDepth));
                }
            }
        }
        List<EtlDtos.LineageNode> nodeViews = nodes.entrySet().stream()
                .map(entry -> new EtlDtos.LineageNode(entry.getKey(), entry.getValue()))
                .toList();
        List<EtlDtos.WorkflowView> related = workflowItems.stream()
                .filter(item -> relatedWorkflows.contains(item.getId()))
                .map(item -> view(item, workflowDependencies.findAll()))
                .toList();
        return new EtlDtos.TableLineage(start, dir, depthLimit, nodeViews, edges, related);
    }

    @Transactional
    public EtlDtos.ImportResult importDolphinScheduler(DolphinSchedulerDtos.ImportRequest request) {
        if (request == null || request.processDefinitions() == null || request.processDefinitions().isEmpty()) {
            throw ApiException.badRequest("processDefinitions 不能为空");
        }
        if (!AuthContext.currentOrSystem().isAdmin()) {
            throw ApiException.accessDenied("需要 ADMIN 角色导入 DolphinScheduler");
        }
        String tenant = request.tenantId() == null || request.tenantId().isBlank()
                ? AuthContext.currentOrSystem().tenantId() : request.tenantId().trim();
        int workflowCount = 0;
        int taskCount = 0;
        int taskDependencyCount = 0;
        int workflowDependencyCount = 0;
        List<String> warnings = new ArrayList<>();
        Map<String, EtlWorkflow> imported = new LinkedHashMap<>();
        Instant now = Instant.now();
        for (DolphinSchedulerDtos.ProcessDefinition definition : request.processDefinitions()) {
            String externalId = externalId(definition);
            if (externalId == null || definition.name() == null) {
                warnings.add("跳过缺少 code/name 的 process definition");
                continue;
            }
            EtlWorkflow workflow = workflows
                    .findByTenantIdAndSourceAndExternalId(tenant, "DOLPHINSCHEDULER", externalId)
                    .orElseGet(EtlWorkflow::new);
            if (workflow.getId() == null) {
                workflow.setTenantId(tenant);
                workflow.setSource("DOLPHINSCHEDULER");
                workflow.setExternalId(externalId);
            }
            workflow.setProject(request.projectName() == null
                    ? (request.projectCode() == null ? null : String.valueOf(request.projectCode()))
                    : request.projectName());
            workflow.setName(definition.name());
            workflow.setDescription(definition.description());
            String cron = definition.schedule() == null ? null
                    : firstNonBlank(definition.schedule().crontab(), definition.schedule().scheduleCron());
            workflow.setScheduleCron(cron);
            workflow.setTimezone(definition.schedule() == null ? null
                    : firstNonBlank(definition.schedule().timezoneId(), definition.schedule().timezone()));
            workflow.setReleaseState(definition.releaseState() == null && definition.schedule() != null
                    ? definition.schedule().releaseState() : definition.releaseState());
            workflow.setEnabled(!"OFFLINE".equalsIgnoreCase(workflow.getReleaseState()));
            workflow.setLastSyncedAt(now);
            workflow = workflows.save(workflow);
            imported.put(externalId, workflow);
            workflowCount++;
            tasks.deleteByWorkflowId(workflow.getId());
            taskDependencies.deleteByWorkflowId(workflow.getId());

            List<DolphinSchedulerDtos.TaskDefinition> definitions = definition.taskDefinitions() != null
                    ? definition.taskDefinitions() : definition.taskDefinitionList();
            Map<Long, EtlTask> byCode = new LinkedHashMap<>();
            int index = 0;
            if (definitions != null) {
                for (DolphinSchedulerDtos.TaskDefinition definitionTask : definitions) {
                    String taskExternalId = definitionTask.code() == null
                            ? "task-" + index : String.valueOf(definitionTask.code());
                    EtlTask task = new EtlTask();
                    task.setWorkflowId(workflow.getId());
                    task.setExternalId(taskExternalId);
                    task.setName(firstNonBlank(definitionTask.name(), taskExternalId));
                    task.setTaskType(firstNonBlank(definitionTask.taskType(), definitionTask.type(), "SQL")
                            .toUpperCase(Locale.ROOT));
                    task.setOrderIndex(definitionTask.taskOrder() == null
                            ? index : definitionTask.taskOrder());
                    task.setEnabled(!"FORBIDDEN".equalsIgnoreCase(definitionTask.flag()));
                    Map<String, Object> params = definitionTask.taskParams() == null
                            ? Map.of() : definitionTask.taskParams();
                    task.setSqlText(sqlText(params));
                    String target = text(params.get("targetTable"));
                    if (target == null) {
                        target = text(params.get("target_table"));
                    }
                    String parsedTarget = lineage.extractTargetTable(task.getSqlText());
                    task.setTargetTable(target == null ? parsedTarget : canonical(target));
                    Set<String> sources = new LinkedHashSet<>(lineage.extractTables(task.getSqlText()));
                    String sourceOverride = text(params.get("sourceTables"));
                    if (sourceOverride != null) {
                        addTables(sources, sourceOverride);
                    }
                    sources.remove(task.getTargetTable());
                    task.setSourceTables(String.join(",", sources));
                    task.setConfigJson(json(params));
                    task = tasks.save(task);
                    if (definitionTask.code() != null) {
                        byCode.put(definitionTask.code(), task);
                    }
                    taskCount++;
                    index++;
                }
            }

            List<DolphinSchedulerDtos.TaskRelation> relations = definition.taskRelations() != null
                    ? definition.taskRelations() : definition.processTaskRelationList();
            Set<String> depKeys = new HashSet<>();
            if (relations != null) {
                for (DolphinSchedulerDtos.TaskRelation relation : relations) {
                    if (relation.preTaskCode() == null || relation.postTaskCode() == null
                            || relation.preTaskCode() == 0) {
                        continue;
                    }
                    EtlTask from = byCode.get(relation.preTaskCode());
                    EtlTask to = byCode.get(relation.postTaskCode());
                    if (from == null || to == null || from.getId().equals(to.getId())) {
                        warnings.add(workflow.getName() + " 跳过无效任务依赖: "
                                + relation.preTaskCode() + " -> " + relation.postTaskCode());
                        continue;
                    }
                    String key = from.getId() + "->" + to.getId();
                    if (!depKeys.add(key)) {
                        continue;
                    }
                    EtlTaskDependency dependency = new EtlTaskDependency();
                    dependency.setWorkflowId(workflow.getId());
                    dependency.setUpstreamTaskId(from.getId());
                    dependency.setDownstreamTaskId(to.getId());
                    dependency.setConditionType(normalizeCondition(relation.conditionType()));
                    dependency.setLagSeconds(relation.lagSeconds() == null ? 0 : relation.lagSeconds());
                    taskDependencies.save(dependency);
                    taskDependencyCount++;
                }
            }
        }

        for (DolphinSchedulerDtos.ProcessDefinition definition : request.processDefinitions()) {
            if (definition.dependencies() == null) {
                continue;
            }
            EtlWorkflow downstream = imported.get(externalId(definition));
            if (downstream == null) {
                continue;
            }
            for (DolphinSchedulerDtos.WorkflowDependency dependency : definition.dependencies()) {
                String upstreamCode = firstNonBlank(
                        dependency.upstreamProcessCode() == null ? null : String.valueOf(dependency.upstreamProcessCode()),
                        dependency.upstreamWorkflowCode() == null ? null : String.valueOf(dependency.upstreamWorkflowCode()));
                if (upstreamCode == null) {
                    continue;
                }
                EtlWorkflow upstream = imported.get(upstreamCode);
                if (upstream == null) {
                    warnings.add("跳过未导入的上游 workflow: " + upstreamCode);
                    continue;
                }
                addWorkflowDependency(upstream.getId(), downstream.getId(),
                        normalizeCondition(dependency.conditionType()),
                        dependency.lagSeconds() == null ? 0 : dependency.lagSeconds());
                workflowDependencyCount++;
            }
        }
        audit.record("ETL_SYNC", "SUCCESS", "ETL", null, null,
                "source=DOLPHINSCHEDULER workflows=" + workflowCount + " tasks=" + taskCount);
        return new EtlDtos.ImportResult(workflowCount, taskCount, taskDependencyCount,
                workflowDependencyCount, warnings);
    }

    private EtlDtos.WorkflowView view(EtlWorkflow workflow, List<EtlWorkflowDependency> deps) {
        int taskCount = tasks.findByWorkflowIdOrderByOrderIndexAsc(workflow.getId()).size();
        long upstream = deps.stream().filter(dep -> dep.getDownstreamWorkflowId().equals(workflow.getId())).count();
        long downstream = deps.stream().filter(dep -> dep.getUpstreamWorkflowId().equals(workflow.getId())).count();
        return new EtlDtos.WorkflowView(workflow.getId(), workflow.getSource(), workflow.getExternalId(),
                workflow.getProject(), workflow.getName(), workflow.getDescription(), workflow.getScheduleCron(),
                workflow.getTimezone(), workflow.getReleaseState(), workflow.isEnabled(), workflow.getOwner(),
                workflow.getLastSyncedAt(), taskCount, nextRun(workflow), (int) upstream, (int) downstream);
    }

    private List<EtlDtos.TaskView> taskViews(UUID workflowId) {
        List<EtlTask> items = tasks.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        List<EtlTaskDependency> deps = taskDependencies.findByWorkflowId(workflowId);
        return items.stream().map(task -> new EtlDtos.TaskView(task.getId(), task.getWorkflowId(),
                task.getExternalId(), task.getName(), task.getTaskType(), task.getOrderIndex(),
                task.getSqlText(), sources(task), task.getTargetTable(), task.getDatasourceId(),
                task.getConfigJson(), task.isEnabled(),
                deps.stream().filter(dep -> dep.getDownstreamTaskId().equals(task.getId()))
                        .map(EtlTaskDependency::getUpstreamTaskId).toList(),
                deps.stream().filter(dep -> dep.getUpstreamTaskId().equals(task.getId()))
                        .map(EtlTaskDependency::getDownstreamTaskId).toList())).toList();
    }

    private Topology topology(List<EtlTask> items, List<EtlTaskDependency> dependencies) {
        Map<UUID, Integer> indegree = new LinkedHashMap<>();
        Map<UUID, List<UUID>> adjacency = new LinkedHashMap<>();
        Map<UUID, EtlTask> byId = new LinkedHashMap<>();
        items.forEach(task -> {
            indegree.put(task.getId(), 0);
            adjacency.put(task.getId(), new ArrayList<>());
            byId.put(task.getId(), task);
        });
        for (EtlTaskDependency dependency : dependencies) {
            if (!byId.containsKey(dependency.getUpstreamTaskId()) || !byId.containsKey(dependency.getDownstreamTaskId())) {
                continue;
            }
            adjacency.get(dependency.getUpstreamTaskId()).add(dependency.getDownstreamTaskId());
            indegree.merge(dependency.getDownstreamTaskId(), 1, Integer::sum);
        }
        Deque<UUID> queue = new ArrayDeque<>();
        indegree.forEach((id, value) -> {
            if (value == 0) {
                queue.add(id);
            }
        });
        List<UUID> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            UUID id = queue.removeFirst();
            order.add(id);
            for (UUID next : adjacency.getOrDefault(id, List.of())) {
                int left = indegree.merge(next, -1, Integer::sum);
                if (left == 0) {
                    queue.add(next);
                }
            }
        }
        List<String> cycles = new ArrayList<>();
        if (order.size() < items.size()) {
            items.stream().filter(task -> !order.contains(task.getId()))
                    .map(EtlTask::getName).forEach(cycles::add);
        }
        Set<UUID> connected = new HashSet<>();
        dependencies.forEach(dep -> {
            connected.add(dep.getUpstreamTaskId());
            connected.add(dep.getDownstreamTaskId());
        });
        List<UUID> orphans = items.size() <= 1 ? List.of()
                : items.stream().filter(task -> !connected.contains(task.getId()))
                .map(EtlTask::getId).toList();
        return new Topology(order, cycles, orphans);
    }

    private CriticalPath criticalPath(List<EtlTask> items, Topology topology) {
        Map<UUID, List<UUID>> adjacency = new LinkedHashMap<>();
        items.forEach(task -> adjacency.put(task.getId(), new ArrayList<>()));
        taskDependencies.findByWorkflowIdIn(items.stream().map(EtlTask::getWorkflowId).distinct().toList())
                .forEach(dep -> adjacency.computeIfAbsent(dep.getUpstreamTaskId(), ignored -> new ArrayList<>())
                        .add(dep.getDownstreamTaskId()));
        Map<UUID, Integer> length = new HashMap<>();
        Map<UUID, UUID> previous = new HashMap<>();
        UUID best = null;
        for (UUID id : topology.order) {
            int value = length.getOrDefault(id, 1);
            if (best == null || value > length.getOrDefault(best, 1)) {
                best = id;
            }
            for (UUID next : adjacency.getOrDefault(id, List.of())) {
                if (value + 1 > length.getOrDefault(next, 1)) {
                    length.put(next, value + 1);
                    previous.put(next, id);
                }
            }
        }
        if (best == null) {
            return new CriticalPath(0, List.of());
        }
        List<UUID> path = new ArrayList<>();
        UUID cursor = best;
        while (cursor != null) {
            path.add(0, cursor);
            cursor = previous.get(cursor);
        }
        return new CriticalPath(length.getOrDefault(best, path.size()), path);
    }

    private List<EtlDtos.WorkflowDependencyChain> chains(UUID start, boolean upstream) {
        List<EtlWorkflowDependency> all = workflowDependencies.findAll();
        Map<UUID, EtlWorkflow> byId = workflows.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(EtlWorkflow::getId, item -> item));
        Map<UUID, List<UUID>> next = new HashMap<>();
        for (EtlWorkflowDependency dependency : all) {
            UUID from = upstream ? dependency.getDownstreamWorkflowId() : dependency.getUpstreamWorkflowId();
            UUID to = upstream ? dependency.getUpstreamWorkflowId() : dependency.getDownstreamWorkflowId();
            next.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
        }
        List<EtlDtos.WorkflowDependencyChain> result = new ArrayList<>();
        Deque<ChainNode> queue = new ArrayDeque<>();
        queue.add(new ChainNode(start, 0, List.of(start)));
        while (!queue.isEmpty()) {
            ChainNode node = queue.removeFirst();
            for (UUID id : next.getOrDefault(node.id(), List.of())) {
                if (node.path().contains(id)) {
                    continue;
                }
                List<UUID> path = new ArrayList<>(node.path());
                path.add(id);
                EtlWorkflow workflow = byId.get(id);
                result.add(new EtlDtos.WorkflowDependencyChain(id,
                        workflow == null ? null : workflow.getName(), node.depth() + 1, path));
                queue.add(new ChainNode(id, node.depth() + 1, path));
            }
        }
        result.sort(Comparator.comparingInt(EtlDtos.WorkflowDependencyChain::depth));
        return result;
    }

    private void addWorkflowDependency(UUID upstream, UUID downstream, String condition, long lag) {
        if (upstream.equals(downstream)) {
            return;
        }
        boolean exists = workflowDependencies.findByUpstreamWorkflowIdOrDownstreamWorkflowId(upstream, upstream)
                .stream().anyMatch(dep -> dep.getUpstreamWorkflowId().equals(upstream)
                        && dep.getDownstreamWorkflowId().equals(downstream));
        if (exists) {
            return;
        }
        EtlWorkflowDependency dependency = new EtlWorkflowDependency();
        dependency.setUpstreamWorkflowId(upstream);
        dependency.setDownstreamWorkflowId(downstream);
        dependency.setConditionType(condition);
        dependency.setLagSeconds(lag);
        workflowDependencies.save(dependency);
    }

    private EtlWorkflow required(UUID id) {
        EtlWorkflow workflow = workflows.findById(id)
                .orElseThrow(() -> ApiException.notFound("ETL workflow 不存在: " + id));
        if (!workflow.getTenantId().equals(AuthContext.currentOrSystem().tenantId())) {
            throw ApiException.accessDenied("不能跨租户访问 ETL workflow");
        }
        return workflow;
    }

    private static List<String> sources(EtlTask task) {
        if (task.getSourceTables() == null || task.getSourceTables().isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : task.getSourceTables().split(",")) {
            if (!value.isBlank()) {
                result.add(canonical(value.trim()));
            }
        }
        return result;
    }

    private static String target(EtlTask task) {
        return task.getTargetTable() == null || task.getTargetTable().isBlank()
                ? null : canonical(task.getTargetTable());
    }

    private static List<String> sourceTables(List<EtlTask> items) {
        return items.stream().flatMap(task -> sources(task).stream()).distinct().sorted().toList();
    }

    private static List<String> targetTables(List<EtlTask> items) {
        return items.stream().map(EtlService::target).filter(java.util.Objects::nonNull)
                .distinct().sorted().toList();
    }

    private static Instant nextRun(EtlWorkflow workflow) {
        if (workflow.getScheduleCron() == null || workflow.getScheduleCron().isBlank()) {
            return null;
        }
        try {
            String cron = workflow.getScheduleCron().trim().replaceAll("\\s+", " ");
            String[] parts = cron.split(" ");
            if (parts.length == 7) {
                cron = String.join(" ", java.util.Arrays.copyOf(parts, 6));
            }
            CronExpression expression = CronExpression.parse(cron);
            ZoneId zone = workflow.getTimezone() == null || workflow.getTimezone().isBlank()
                    ? ZoneId.systemDefault() : ZoneId.of(workflow.getTimezone());
            ZonedDateTime next = expression.next(ZonedDateTime.now(zone));
            return next == null ? null : next.toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String externalId(DolphinSchedulerDtos.ProcessDefinition definition) {
        if (definition.code() != null) {
            return String.valueOf(definition.code());
        }
        return definition.name();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String sqlText(Map<String, Object> params) {
        Object value = params.get("sql");
        if (value == null) {
            value = params.get("rawScript");
        }
        if (value == null) {
            value = params.get("script");
        }
        return value == null ? null : String.valueOf(value);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void addTables(Set<String> tables, String value) {
        for (String piece : value.split("[,;；\\s]+")) {
            if (!piece.isBlank()) {
                tables.add(canonical(piece.trim()));
            }
        }
    }

    private static String normalizeCondition(String value) {
        if (value == null || value.isBlank()) {
            return "ALL_SUCCESS";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String json(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception error) {
            return "{}";
        }
    }

    private static String canonical(String value) {
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        int dot = upper.lastIndexOf('.');
        return dot >= 0 ? upper.substring(dot + 1) : upper;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record TableDepth(String table, int depth) {
    }

    private record EtlTaskEdge(String source, String target, EtlTask task, EtlWorkflow workflow) {
    }

    private record ChainNode(UUID id, int depth, List<UUID> path) {
    }

    private record Topology(List<UUID> order, List<String> cycles, List<UUID> orphans) {
    }

    private record CriticalPath(int length, List<UUID> path) {
    }
}
