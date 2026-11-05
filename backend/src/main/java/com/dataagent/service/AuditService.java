package com.dataagent.service;

import com.dataagent.domain.AuditEvent;
import com.dataagent.repository.AuditEventRepository;
import com.dataagent.security.ApiTokenFilter;
import com.dataagent.web.dto.AuditDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_DETAIL = 8000;

    private final AuditEventRepository events;
    private final TransactionTemplate transactionTemplate;

    public AuditService(AuditEventRepository events, PlatformTransactionManager transactionManager) {
        this.events = events;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(String action, String status, String resourceType, Object resourceId,
                       java.util.UUID datasourceId, String detail) {
        try {
            HttpServletRequest request = currentRequest();
            String actor = request == null ? "system" : (String) request.getAttribute(ApiTokenFilter.USER_ATTRIBUTE);
            if (actor == null || actor.isBlank()) {
                actor = "system";
            }
            String clientIp = request == null ? null : request.getRemoteAddr();
            String userAgent = request == null ? null : truncate(request.getHeader("User-Agent"), 300);
            String finalActor = actor;
            transactionTemplate.executeWithoutResult(ignored -> {
                AuditEvent event = new AuditEvent();
                event.setActor(finalActor);
                event.setAction(action.toUpperCase(Locale.ROOT));
                event.setResourceType(resourceType);
                event.setResourceId(resourceId == null ? null : truncate(resourceId.toString(), 200));
                event.setDatasourceId(datasourceId);
                event.setStatus(status.toUpperCase(Locale.ROOT));
                event.setDetail(truncate(detail, MAX_DETAIL));
                event.setClientIp(clientIp);
                event.setUserAgent(userAgent);
                events.save(event);
            });
        } catch (Exception error) {
            log.warn("Audit write failed for {}: {}", action, error.getMessage());
        }
    }

    public List<AuditDtos.View> search(String actor, String action, String resourceType,
                                       String status, Instant from, Instant to, int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int safeOffset = Math.max(0, offset);
        return events.search(blank(actor) ? null : actor.trim(),
                        blank(action) ? null : action.trim(),
                        blank(resourceType) ? null : resourceType.trim(),
                        blank(status) ? null : status.trim(),
                        from, to, PageRequest.of(safeOffset / safeLimit, safeLimit))
                .stream().map(this::view).toList();
    }

    public AuditDtos.Overview overview() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long total = events.countByCreatedAtAfter(since);
        long failed = events.countByStatusAndCreatedAtAfter("ERROR", since);
        long blocked = events.countByActionAndCreatedAtAfter("SQL_BLOCKED", since);
        List<AuditDtos.ActionCount> top = events.search(null, null, null, null, since, null,
                        PageRequest.of(0, 1000)).stream()
                .collect(java.util.stream.Collectors.groupingBy(AuditEvent::getAction,
                        LinkedHashMap::new, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new AuditDtos.ActionCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(AuditDtos.ActionCount::count).reversed())
                .limit(8)
                .toList();
        return new AuditDtos.Overview(total, failed, blocked, top);
    }

    private AuditDtos.View view(AuditEvent event) {
        return new AuditDtos.View(event.getId(), event.getActor(), event.getAction(),
                event.getResourceType(), event.getResourceId(), event.getDatasourceId(),
                event.getStatus(), event.getDetail(), event.getClientIp(), event.getUserAgent(),
                event.getCreatedAt());
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
