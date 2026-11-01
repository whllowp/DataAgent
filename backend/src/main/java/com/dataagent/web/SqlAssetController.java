package com.dataagent.web;

import com.dataagent.security.ApiTokenFilter;
import com.dataagent.service.SqlAssetService;
import com.dataagent.web.dto.SqlAssetDtos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sql-assets")
public class SqlAssetController {

    private final SqlAssetService assets;

    public SqlAssetController(SqlAssetService assets) {
        this.assets = assets;
    }

    @GetMapping
    public SqlAssetDtos.AssetPage search(@RequestParam(required = false, name = "q") String query,
                                         @RequestParam(required = false) String domain,
                                         @RequestParam(required = false) UUID datasourceId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return assets.search(query, domain, datasourceId, page, size);
    }

    @GetMapping("/stats")
    public SqlAssetDtos.CacheStats stats() {
        return assets.stats();
    }

    @GetMapping("/{id}")
    public SqlAssetDtos.SqlAssetView get(@PathVariable UUID id) {
        return assets.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SqlAssetDtos.SqlAssetView save(@Valid @RequestBody SqlAssetDtos.SaveRequest request,
                                          @RequestAttribute(name = ApiTokenFilter.USER_ATTRIBUTE) String user) {
        return assets.saveDraft(request, user);
    }

    @PostMapping("/import")
    public Map<String, Object> importAssets(@Valid @RequestBody SqlAssetDtos.ImportRequest request,
                                            @RequestAttribute(name = ApiTokenFilter.USER_ATTRIBUTE) String user) {
        int imported = assets.importAssets(request, user);
        return Map.of("imported", imported);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        assets.delete(id);
    }
}
