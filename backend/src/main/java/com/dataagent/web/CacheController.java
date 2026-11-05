package com.dataagent.web;

import com.dataagent.service.AuditService;
import com.dataagent.service.QueryResultCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops/cache")
public class CacheController {

    private final QueryResultCache cache;
    private final AuditService audit;

    public CacheController(QueryResultCache cache, AuditService audit) {
        this.cache = cache;
        this.audit = audit;
    }

    @GetMapping
    public QueryResultCache.CacheStats stats() {
        return cache.stats();
    }

    @PostMapping("/clear")
    public QueryResultCache.CacheStats clear() {
        cache.clear();
        audit.record("QUERY_CACHE_CLEAR", "SUCCESS", "CACHE", null, null, "manual clear");
        return cache.stats();
    }
}
