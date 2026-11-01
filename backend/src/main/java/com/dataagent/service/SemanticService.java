package com.dataagent.service;

import com.dataagent.domain.KnowledgeEntry;
import com.dataagent.domain.SemanticEntity;
import com.dataagent.domain.SemanticLink;
import com.dataagent.domain.SemanticMetric;
import com.dataagent.domain.SemanticSyncRun;
import com.dataagent.domain.SemanticTerm;
import com.dataagent.domain.SqlAsset;
import com.dataagent.repository.KnowledgeEntryRepository;
import com.dataagent.repository.SemanticEntityRepository;
import com.dataagent.repository.SemanticLinkRepository;
import com.dataagent.repository.SemanticMetricRepository;
import com.dataagent.repository.SemanticSyncRunRepository;
import com.dataagent.repository.SemanticTermRepository;
import com.dataagent.repository.SqlAssetRepository;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.KnowledgeDtos;
import com.dataagent.web.dto.SemanticDtos;
import com.dataagent.web.dto.SqlAssetDtos;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SemanticService {

    private final SemanticEntityRepository entities;
    private final SemanticMetricRepository metrics;
    private final SemanticTermRepository terms;
    private final SemanticLinkRepository links;
    private final SemanticSyncRunRepository syncRuns;
    private final KnowledgeEntryRepository knowledge;
    private final SqlAssetRepository assets;
    private final KnowledgeService knowledgeService;
    private final SqlAssetService sqlAssetService;

    public SemanticService(SemanticEntityRepository entities, SemanticMetricRepository metrics,
                           SemanticTermRepository terms, SemanticLinkRepository links,
                           SemanticSyncRunRepository syncRuns, KnowledgeEntryRepository knowledge,
                           SqlAssetRepository assets, KnowledgeService knowledgeService,
                           SqlAssetService sqlAssetService) {
        this.entities = entities;
        this.metrics = metrics;
        this.terms = terms;
        this.links = links;
        this.syncRuns = syncRuns;
        this.knowledge = knowledge;
        this.assets = assets;
        this.knowledgeService = knowledgeService;
        this.sqlAssetService = sqlAssetService;
    }

    @Transactional(readOnly = true)
    public List<SemanticDtos.EntityView> listEntities(String query, String domain) {
        return searchEntities(query, domain);
    }

    @Transactional
    public SemanticDtos.EntityView createEntity(SemanticDtos.EntityRequest request) {
        String name = request.name().trim();
        entities.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw ApiException.badRequest("语义实体名称已存在: " + name);
        });
        SemanticEntity entity = new SemanticEntity();
        applyEntity(entity, request);
        entities.save(entity);
        return toEntityView(entity);
    }

    @Transactional
    public SemanticDtos.EntityView updateEntity(UUID id, SemanticDtos.EntityRequest request) {
        SemanticEntity entity = entities.findById(id)
                .orElseThrow(() -> ApiException.notFound("语义实体不存在: " + id));
        String name = request.name().trim();
        entities.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ApiException.badRequest("语义实体名称已存在: " + name);
                });
        applyEntity(entity, request);
        entities.save(entity);
        return toEntityView(entity);
    }

    @Transactional
    public void deleteEntity(UUID id) {
        if (!entities.existsById(id)) {
            throw ApiException.notFound("语义实体不存在: " + id);
        }
        links.deleteByTargetTypeAndTargetId("ENTITY", id);
        entities.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<SemanticDtos.MetricView> listMetrics(String query) {
        Map<UUID, String> entityNames = entityNames();
        return searchMetrics(query).stream()
                .map(metric -> toMetricView(metric, entityNames))
                .toList();
    }

    @Transactional
    public SemanticDtos.MetricView createMetric(SemanticDtos.MetricRequest request) {
        String name = request.name().trim();
        metrics.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw ApiException.badRequest("指标名称已存在: " + name);
        });
        validateEntity(request.entityId());
        SemanticMetric metric = new SemanticMetric();
        applyMetric(metric, request);
        metrics.save(metric);
        return toMetricView(metric, entityNames());
    }

    @Transactional
    public SemanticDtos.MetricView updateMetric(UUID id, SemanticDtos.MetricRequest request) {
        SemanticMetric metric = metrics.findById(id)
                .orElseThrow(() -> ApiException.notFound("指标不存在: " + id));
        String name = request.name().trim();
        metrics.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ApiException.badRequest("指标名称已存在: " + name);
                });
        validateEntity(request.entityId());
        applyMetric(metric, request);
        metrics.save(metric);
        return toMetricView(metric, entityNames());
    }

    @Transactional
    public void deleteMetric(UUID id) {
        if (!metrics.existsById(id)) {
            throw ApiException.notFound("指标不存在: " + id);
        }
        links.deleteByTargetTypeAndTargetId("METRIC", id);
        metrics.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<SemanticDtos.TermView> listTerms(String query) {
        Map<UUID, String> entityNames = entityNames();
        Map<UUID, String> metricNames = metricNames();
        return searchTerms(query).stream()
                .map(term -> toTermView(term, entityNames, metricNames))
                .toList();
    }

    @Transactional
    public SemanticDtos.TermView createTerm(SemanticDtos.TermRequest request) {
        String termValue = request.term().trim();
        terms.findByTermIgnoreCase(termValue).ifPresent(existing -> {
            throw ApiException.badRequest("术语已存在: " + termValue);
        });
        validateRelated(request.relatedEntityId(), request.relatedMetricId());
        SemanticTerm term = new SemanticTerm();
        applyTerm(term, request);
        terms.save(term);
        return toTermView(term, entityNames(), metricNames());
    }

    @Transactional
    public SemanticDtos.TermView updateTerm(UUID id, SemanticDtos.TermRequest request) {
        SemanticTerm term = terms.findById(id)
                .orElseThrow(() -> ApiException.notFound("术语不存在: " + id));
        String termValue = request.term().trim();
        terms.findByTermIgnoreCase(termValue)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw ApiException.badRequest("术语已存在: " + termValue);
                });
        validateRelated(request.relatedEntityId(), request.relatedMetricId());
        applyTerm(term, request);
        terms.save(term);
        return toTermView(term, entityNames(), metricNames());
    }

    @Transactional
    public void deleteTerm(UUID id) {
        if (!terms.existsById(id)) {
            throw ApiException.notFound("术语不存在: " + id);
        }
        links.deleteByTargetTypeAndTargetId("TERM", id);
        terms.deleteById(id);
    }

    @Transactional(readOnly = true)
    public SemanticDtos.SemanticContext context(String query, String domain) {
        List<SemanticDtos.EntityView> matchedEntities = new ArrayList<>(listEntities(query, domain));
        List<SemanticDtos.MetricView> matchedMetrics = listMetrics(query);
        List<SemanticDtos.TermView> matchedTerms = listTerms(query);

        // A metric implies its entity even when the user only typed the metric name.
        Set<UUID> entityIds = matchedEntities.stream().map(SemanticDtos.EntityView::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (SemanticDtos.MetricView metric : matchedMetrics) {
            if (metric.entityId() != null && entityIds.add(metric.entityId())) {
                entities.findById(metric.entityId()).ifPresent(entity -> matchedEntities.add(toEntityView(entity)));
            }
        }
        for (SemanticDtos.TermView term : matchedTerms) {
            if (term.relatedEntityId() != null && entityIds.add(term.relatedEntityId())) {
                entities.findById(term.relatedEntityId()).ifPresent(entity -> matchedEntities.add(toEntityView(entity)));
            }
        }

        Set<UUID> knowledgeIds = new LinkedHashSet<>();
        Set<UUID> assetIds = new LinkedHashSet<>();
        collectSources("ENTITY", matchedEntities.stream().map(SemanticDtos.EntityView::id).toList(), knowledgeIds, assetIds);
        collectSources("METRIC", matchedMetrics.stream().map(SemanticDtos.MetricView::id).toList(), knowledgeIds, assetIds);
        collectSources("TERM", matchedTerms.stream().map(SemanticDtos.TermView::id).toList(), knowledgeIds, assetIds);

        List<KnowledgeDtos.KnowledgeView> linkedKnowledge = knowledge.findAllById(knowledgeIds).stream()
                .map(knowledgeService::viewOf)
                .limit(8)
                .toList();
        List<SqlAssetDtos.SqlAssetView> linkedAssets = assets.findAllById(assetIds).stream()
                .map(sqlAssetService::toView)
                .limit(8)
                .toList();

        return new SemanticDtos.SemanticContext(
                query, matchedEntities, matchedMetrics, matchedTerms, linkedKnowledge, linkedAssets);
    }

    @Transactional(readOnly = true)
    public SemanticDtos.DesignView design(SemanticDtos.DesignRequest request) {
        SemanticDtos.SemanticContext context = context(request.idea(), request.domain());
        List<SqlAssetDtos.SqlAssetView> assetsToUse = context.assets();
        if (assetsToUse.isEmpty()) {
            assetsToUse = assets.search(request.datasourceId(), blankToNull(request.domain()), null,
                            PageRequest.of(0, 5))
                    .getContent().stream().map(sqlAssetService::toView).toList();
        } else if (request.datasourceId() != null) {
            assetsToUse = assetsToUse.stream()
                    .filter(asset -> request.datasourceId().equals(asset.datasourceId()))
                    .limit(5)
                    .toList();
        }

        List<String> angles = new ArrayList<>();
        angles.add("业务目标：先写清这个能力改变哪个决策或动作，再定义成功指标与验收口径。");
        if (!context.metrics().isEmpty()) {
            angles.add("指标与口径：复用 " + names(context.metrics().stream()
                    .map(SemanticDtos.MetricView::displayName).toList()) + "，不要在新功能里另造一套同名指标。");
        } else {
            angles.add("指标与口径：语义层还没有匹配指标，应先补一个可复用指标（名称、定义、表达式、维度）。");
        }
        if (!context.entities().isEmpty()) {
            angles.add("数据实体：优先从 " + names(context.entities().stream()
                    .map(SemanticDtos.EntityView::displayName).toList()) + " 出发，明确主键、粒度、时间字段与关联路径。");
        } else {
            angles.add("数据实体：语义层没有匹配实体，需要先确认真实表、主键、时间字段和跨表 JOIN 关系。");
        }
        angles.add("维度切分：至少定义时间、组织/区域、产品/渠道等常用维度，并说明哪些维度不能下钻。");
        angles.add("数据质量与权限：明确迟到数据、去重、退款/取消、NULL 语义，以及字段级权限与脱敏。");
        angles.add("交付路径：语义定义 → SQL/数据模型 → API/页面 → 指标对账 → 灰度与验收。");

        List<String> questions = new ArrayList<>();
        if (context.metrics().isEmpty()) {
            questions.add("这个需求最终要看哪个指标？它的业务定义、计算公式和时间窗口是什么？");
        }
        if (context.entities().isEmpty()) {
            questions.add("需求涉及哪些业务实体？每个实体的主键、粒度和数据来源表是什么？");
        }
        questions.add("统计粒度和时间范围是什么？是否需要历史回溯、同比/环比或实时性？");
        questions.add("哪些维度必须支持？是否存在组织、区域、渠道、产品等权限边界？");
        questions.add("结果异常时以哪张表/哪个指标为对账基准？谁负责口径确认？");

        List<String> risks = new ArrayList<>();
        if (context.terms().isEmpty()) {
            risks.add("语义层尚未覆盖需求中的关键术语，容易出现同名不同义。");
        }
        if (context.metrics().isEmpty()) {
            risks.add("缺少可复用指标定义，新增功能可能重复实现口径。");
        }
        risks.add("历史 SQL 可能包含旧口径或临时过滤条件，不能直接当作正式业务定义。");
        risks.add("跨实体 JOIN 可能存在一对多放大；需要先确认事实表粒度与去重规则。");

        String prompt = buildDesignPrompt(request, context, assetsToUse, angles, questions, risks);
        return new SemanticDtos.DesignView(
                request.idea(),
                blankToNull(request.domain()),
                context.entities(),
                context.metrics(),
                context.terms(),
                angles,
                questions,
                risks,
                context.knowledge(),
                assetsToUse,
                prompt);
    }

    @Transactional(readOnly = true)
    public List<SemanticDtos.LinkView> linksForTarget(String targetType, UUID targetId) {
        return links.findByTargetTypeAndTargetIdOrderByConfidenceDesc(targetType.toUpperCase(Locale.ROOT), targetId)
                .stream().map(this::toLinkView).toList();
    }

    @Transactional(readOnly = true)
    public List<SemanticDtos.LinkView> linksForSource(String sourceType, UUID sourceId) {
        return links.findBySourceTypeAndSourceIdOrderByConfidenceDesc(sourceType.toUpperCase(Locale.ROOT), sourceId)
                .stream().map(this::toLinkView).toList();
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDtos.KnowledgeView> relatedKnowledge(String targetType, UUID targetId) {
        List<UUID> ids = links.findByTargetTypeAndTargetIdOrderByConfidenceDesc(
                        targetType.toUpperCase(Locale.ROOT), targetId).stream()
                .filter(link -> "KNOWLEDGE".equals(link.getSourceType()))
                .map(SemanticLink::getSourceId)
                .distinct()
                .toList();
        return knowledge.findAllById(ids).stream().map(knowledgeService::viewOf).toList();
    }

    @Transactional(readOnly = true)
    public List<SqlAssetDtos.SqlAssetView> relatedAssets(String targetType, UUID targetId) {
        List<UUID> ids = links.findByTargetTypeAndTargetIdOrderByConfidenceDesc(
                        targetType.toUpperCase(Locale.ROOT), targetId).stream()
                .filter(link -> "SQL_ASSET".equals(link.getSourceType()))
                .map(SemanticLink::getSourceId)
                .distinct()
                .toList();
        return assets.findAllById(ids).stream().map(sqlAssetService::toView).toList();
    }

    @Transactional(readOnly = true)
    public SemanticDtos.SemanticOverview overview() {
        List<SemanticLink> allLinks = links.findAll();
        long knowledgeLinked = allLinks.stream()
                .filter(link -> "KNOWLEDGE".equals(link.getSourceType()))
                .map(SemanticLink::getSourceId)
                .distinct()
                .count();
        long assetsLinked = allLinks.stream()
                .filter(link -> "SQL_ASSET".equals(link.getSourceType()))
                .map(SemanticLink::getSourceId)
                .distinct()
                .count();
        List<SemanticSyncRun> recent = syncRuns.findTop10ByOrderByStartedAtDesc();
        return new SemanticDtos.SemanticOverview(
                entities.count(), metrics.count(), terms.count(), allLinks.size(),
                knowledgeLinked, assetsLinked,
                recent.isEmpty() ? null : toSyncRunView(recent.get(0)),
                recent.stream().map(this::toSyncRunView).toList());
    }

    public SemanticDtos.SyncRunView toSyncRunView(SemanticSyncRun run) {
        return new SemanticDtos.SyncRunView(
                run.getId(), run.getTriggerType(), run.getStatus(), run.getStartedAt(), run.getFinishedAt(),
                run.getScannedAssets(), run.getScannedKnowledge(), run.getCreatedLinks(),
                run.getUpdatedLinks(), run.getRemovedLinks(), run.getMessage());
    }

    private void collectSources(String targetType, Collection<UUID> targetIds,
                                Set<UUID> knowledgeIds, Set<UUID> assetIds) {
        for (UUID targetId : targetIds) {
            for (SemanticLink link : links.findByTargetTypeAndTargetIdOrderByConfidenceDesc(targetType, targetId)) {
                if ("KNOWLEDGE".equals(link.getSourceType())) {
                    knowledgeIds.add(link.getSourceId());
                } else if ("SQL_ASSET".equals(link.getSourceType())) {
                    assetIds.add(link.getSourceId());
                }
            }
        }
    }

    private List<SemanticDtos.EntityView> searchEntities(String query, String domain) {
        Map<UUID, SemanticDtos.EntityView> result = new java.util.LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            entities.search(null, blankToNull(domain)).forEach(entity -> result.put(entity.getId(), toEntityView(entity)));
            return new ArrayList<>(result.values());
        }
        for (String token : tokens(query)) {
            entities.search(token, blankToNull(domain))
                    .forEach(entity -> result.put(entity.getId(), toEntityView(entity)));
        }
        return new ArrayList<>(result.values());
    }

    private List<SemanticMetric> searchMetrics(String query) {
        Map<UUID, SemanticMetric> result = new java.util.LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return metrics.search(null);
        }
        for (String token : tokens(query)) {
            metrics.search(token).forEach(metric -> result.put(metric.getId(), metric));
        }
        return new ArrayList<>(result.values());
    }

    private List<SemanticTerm> searchTerms(String query) {
        Map<UUID, SemanticTerm> result = new java.util.LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            return terms.search(null);
        }
        for (String token : tokens(query)) {
            terms.search(token).forEach(term -> result.put(term.getId(), term));
        }
        return new ArrayList<>(result.values());
    }

    private List<String> tokens(String query) {
        return java.util.Arrays.stream(query.trim().split("[\\s,，/;；|]+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .distinct()
                .toList();
    }

    private void applyEntity(SemanticEntity entity, SemanticDtos.EntityRequest request) {
        entity.setName(request.name().trim());
        entity.setDisplayName(request.displayName().trim());
        entity.setDomain(blankToNull(request.domain()));
        entity.setDescription(blankToNull(request.description()));
        entity.setPhysicalTables(blankToNull(request.physicalTables()));
        entity.setSynonyms(blankToNull(request.synonyms()));
        entity.setOwner(blankToNull(request.owner()));
        entity.setTags(blankToNull(request.tags()));
        entity.setStatus(status(request.status()));
    }

    private void applyMetric(SemanticMetric metric, SemanticDtos.MetricRequest request) {
        metric.setName(request.name().trim());
        metric.setDisplayName(request.displayName().trim());
        metric.setEntityId(request.entityId());
        metric.setBusinessDefinition(blankToNull(request.businessDefinition()));
        metric.setExpression(blankToNull(request.expression()));
        metric.setDimensions(blankToNull(request.dimensions()));
        metric.setSynonyms(blankToNull(request.synonyms()));
        metric.setOwner(blankToNull(request.owner()));
        metric.setTags(blankToNull(request.tags()));
        metric.setStatus(status(request.status()));
    }

    private void applyTerm(SemanticTerm term, SemanticDtos.TermRequest request) {
        term.setTerm(request.term().trim());
        term.setDefinition(blankToNull(request.definition()));
        term.setSynonyms(blankToNull(request.synonyms()));
        term.setRelatedEntityId(request.relatedEntityId());
        term.setRelatedMetricId(request.relatedMetricId());
        term.setOwner(blankToNull(request.owner()));
        term.setTags(blankToNull(request.tags()));
        term.setStatus(status(request.status()));
    }

    private void validateEntity(UUID entityId) {
        if (entityId != null && !entities.existsById(entityId)) {
            throw ApiException.badRequest("关联的语义实体不存在: " + entityId);
        }
    }

    private void validateRelated(UUID entityId, UUID metricId) {
        validateEntity(entityId);
        if (metricId != null && !metrics.existsById(metricId)) {
            throw ApiException.badRequest("关联的指标不存在: " + metricId);
        }
    }

    private SemanticDtos.EntityView toEntityView(SemanticEntity entity) {
        return new SemanticDtos.EntityView(
                entity.getId(), entity.getName(), entity.getDisplayName(), entity.getDomain(),
                entity.getDescription(), entity.getPhysicalTables(), entity.getSynonyms(),
                entity.getOwner(), entity.getTags(), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private SemanticDtos.MetricView toMetricView(SemanticMetric metric, Map<UUID, String> entityNames) {
        return new SemanticDtos.MetricView(
                metric.getId(), metric.getName(), metric.getDisplayName(), metric.getEntityId(),
                metric.getEntityId() == null ? null : entityNames.get(metric.getEntityId()),
                metric.getBusinessDefinition(), metric.getExpression(), metric.getDimensions(),
                metric.getSynonyms(), metric.getOwner(), metric.getTags(), metric.getStatus(),
                metric.getCreatedAt(), metric.getUpdatedAt());
    }

    private SemanticDtos.TermView toTermView(SemanticTerm term, Map<UUID, String> entityNames,
                                             Map<UUID, String> metricNames) {
        return new SemanticDtos.TermView(
                term.getId(), term.getTerm(), term.getDefinition(), term.getSynonyms(),
                term.getRelatedEntityId(),
                term.getRelatedEntityId() == null ? null : entityNames.get(term.getRelatedEntityId()),
                term.getRelatedMetricId(),
                term.getRelatedMetricId() == null ? null : metricNames.get(term.getRelatedMetricId()),
                term.getOwner(), term.getTags(), term.getStatus(),
                term.getCreatedAt(), term.getUpdatedAt());
    }

    private SemanticDtos.LinkView toLinkView(SemanticLink link) {
        return new SemanticDtos.LinkView(
                link.getId(), link.getSourceType(), link.getSourceId(), link.getTargetType(), link.getTargetId(),
                targetName(link.getTargetType(), link.getTargetId()), link.getConfidence(),
                link.getReason(), link.getLinkSource(), link.getUpdatedAt());
    }

    private String targetName(String targetType, UUID targetId) {
        return switch (targetType) {
            case "ENTITY" -> entities.findById(targetId).map(SemanticEntity::getDisplayName).orElse(null);
            case "METRIC" -> metrics.findById(targetId).map(SemanticMetric::getDisplayName).orElse(null);
            case "TERM" -> terms.findById(targetId).map(SemanticTerm::getTerm).orElse(null);
            default -> null;
        };
    }

    private Map<UUID, String> entityNames() {
        return entities.findAll().stream().collect(Collectors.toMap(
                SemanticEntity::getId, SemanticEntity::getDisplayName, (a, b) -> a, HashMap::new));
    }

    private Map<UUID, String> metricNames() {
        return metrics.findAll().stream().collect(Collectors.toMap(
                SemanticMetric::getId, SemanticMetric::getDisplayName, (a, b) -> a, HashMap::new));
    }

    private String buildDesignPrompt(SemanticDtos.DesignRequest request, SemanticDtos.SemanticContext context,
                                     List<SqlAssetDtos.SqlAssetView> relatedAssets,
                                     List<String> angles, List<String> questions, List<String> risks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 DataAgent 的业务语义与数据产品设计助手。请基于语义层和团队知识库给出可落地的设计，而不是泛泛而谈。\n\n");
        prompt.append("## 新特性 / 业务想法\n").append(request.idea()).append("\n\n");
        if (request.domain() != null && !request.domain().isBlank()) {
            prompt.append("## 业务域\n").append(request.domain()).append("\n\n");
        }
        prompt.append("## 已匹配语义实体\n").append(context.entities().isEmpty() ? "无\n" :
                context.entities().stream().map(e -> e.displayName() + "(" + e.name() + ")").toList() + "\n");
        prompt.append("\n## 已匹配指标\n").append(context.metrics().isEmpty() ? "无\n" :
                context.metrics().stream().map(m -> m.displayName() + " = " + nullToEmpty(m.businessDefinition())).toList() + "\n");
        prompt.append("\n## 已匹配术语\n").append(context.terms().isEmpty() ? "无\n" :
                context.terms().stream().map(t -> t.term() + ": " + nullToEmpty(t.definition())).toList() + "\n");
        prompt.append("\n## 团队知识库关联\n").append(context.knowledge().isEmpty() ? "无\n" :
                context.knowledge().stream().map(k -> "- " + k.title() + ": " + nullToEmpty(k.problem())).toList() + "\n");
        prompt.append("\n## 历史 SQL 资产\n");
        if (relatedAssets.isEmpty()) {
            prompt.append("无\n");
        } else {
            for (SqlAssetDtos.SqlAssetView asset : relatedAssets) {
                prompt.append("- 业务问题: ").append(nullToEmpty(asset.businessProblem()))
                        .append(" | SQL: ").append(truncate(asset.sqlText(), 500)).append('\n');
            }
        }
        prompt.append("\n## 建议设计角度\n");
        angles.forEach(angle -> prompt.append("- ").append(angle).append('\n'));
        prompt.append("\n## 需要澄清的问题\n");
        questions.forEach(question -> prompt.append("- ").append(question).append('\n'));
        prompt.append("\n## 风险\n");
        risks.forEach(risk -> prompt.append("- ").append(risk).append('\n'));
        prompt.append("""

                ## 输出要求
                请输出：
                1. 业务目标与成功指标
                2. 语义模型（实体、粒度、指标口径、维度、关系）
                3. 数据模型/表与字段建议（明确哪些是已有、哪些需新增）
                4. 功能/API 设计与交互流程
                5. 数据质量、权限与合规风险
                6. 验收标准与验证 SQL
                7. 待确认问题
                不要编造不存在的表、字段或指标；不确定时明确标注“待确认”。
                """);
        return prompt.toString();
    }

    private String names(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank()).collect(Collectors.joining("、"));
    }

    private String status(String value) {
        return value == null || value.isBlank() ? "ACTIVE" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
