package com.dataagent.service;

import com.dataagent.domain.KnowledgeEntry;
import com.dataagent.repository.KnowledgeEntryRepository;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.KnowledgeDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private final KnowledgeEntryRepository repository;

    public KnowledgeService(KnowledgeEntryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public KnowledgeDtos.KnowledgePage search(String query, String dialect, String tag, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<KnowledgeEntry> result = repository.search(
                blankToNull(query), blankToNull(dialect == null ? null : dialect.toUpperCase(Locale.ROOT)),
                blankToNull(tag), pageable);
        return new KnowledgeDtos.KnowledgePage(
                result.getContent().stream().map(this::toView).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public KnowledgeDtos.KnowledgeView get(UUID id) {
        return toView(getRequired(id));
    }

    @Transactional(readOnly = true)
    public KnowledgeEntry getRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("知识条目不存在: " + id));
    }

    @Transactional
    public KnowledgeDtos.KnowledgeView create(KnowledgeDtos.KnowledgeRequest request, String user) {
        KnowledgeEntry entry = new KnowledgeEntry();
        apply(entry, request, user);
        repository.save(entry);
        return toView(entry);
    }

    @Transactional
    public KnowledgeDtos.KnowledgeView update(UUID id, KnowledgeDtos.KnowledgeRequest request, String user) {
        KnowledgeEntry entry = getRequired(id);
        apply(entry, request, user);
        repository.save(entry);
        return toView(entry);
    }

    @Transactional
    public KnowledgeDtos.KnowledgeView markUsed(UUID id) {
        KnowledgeEntry entry = getRequired(id);
        entry.setUsageCount(entry.getUsageCount() + 1);
        repository.save(entry);
        return toView(entry);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw ApiException.notFound("知识条目不存在: " + id);
        }
        repository.deleteById(id);
    }

    private void apply(KnowledgeEntry entry, KnowledgeDtos.KnowledgeRequest request, String user) {
        entry.setTitle(request.title().trim());
        entry.setProblem(blankToNull(request.problem()));
        entry.setSqlText(request.sqlText());
        entry.setOptimizedSql(blankToNull(request.optimizedSql()));
        entry.setDialect(request.dialect() == null ? null : request.dialect().toUpperCase(Locale.ROOT));
        entry.setTags(normalizeTags(request.tags()));
        entry.setPlanSummary(blankToNull(request.planSummary()));
        entry.setRootCause(blankToNull(request.rootCause()));
        entry.setOptimizationSteps(blankToNull(request.optimizationSteps()));
        entry.setBeforeDurationMs(request.beforeDurationMs());
        entry.setAfterDurationMs(request.afterDurationMs());
        entry.setAuthor(request.author() == null || request.author().isBlank() ? user : request.author());
        entry.setSourceQueryId(request.sourceQueryId());
    }

    public String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return null;
        }
        Set<String> normalized = Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.length() > 40 ? value.substring(0, 40) : value)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String joined = String.join(",", normalized);
        return joined.length() > 500 ? joined.substring(0, 500) : joined;
    }

    public KnowledgeDtos.KnowledgeView viewOf(KnowledgeEntry entry) {
        return toView(entry);
    }

    private KnowledgeDtos.KnowledgeView toView(KnowledgeEntry entry) {
        return new KnowledgeDtos.KnowledgeView(
                entry.getId(),
                entry.getTitle(),
                entry.getProblem(),
                entry.getSqlText(),
                entry.getOptimizedSql(),
                entry.getDialect(),
                entry.getTags(),
                entry.getPlanSummary(),
                entry.getRootCause(),
                entry.getOptimizationSteps(),
                entry.getBeforeDurationMs(),
                entry.getAfterDurationMs(),
                entry.getAuthor(),
                entry.getSourceQueryId(),
                entry.getUsageCount(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
