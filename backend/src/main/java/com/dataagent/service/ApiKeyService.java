package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.domain.ApiKey;
import com.dataagent.repository.ApiKeyRepository;
import com.dataagent.security.AuthContext;
import com.dataagent.security.Principal;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.ApiKeyDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository keys;
    private final DataAgentProperties properties;
    private final AuditService audit;

    public ApiKeyService(ApiKeyRepository keys, DataAgentProperties properties, AuditService audit) {
        this.keys = keys;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional
    public ApiKeyDtos.Created create(ApiKeyDtos.CreateRequest request) {
        requireAdmin();
        Principal principal = AuthContext.currentOrSystem();
        String role = normalizeRole(request.role());
        boolean globalAdmin = isGlobalAdmin(principal);
        String tenant = globalAdmin && request.tenantId() != null && !request.tenantId().isBlank()
                ? request.tenantId().trim() : principal.tenantId();
        String prefix = random(6);
        String secret = random(32);
        String plaintext = "da_" + prefix + "_" + secret;
        ApiKey key = new ApiKey();
        key.setName(request.name() == null || request.name().isBlank() ? "api-key" : request.name().trim());
        key.setKeyPrefix(prefix);
        key.setKeyHash(hash(plaintext));
        key.setRole(role);
        key.setTenantId(tenant);
        key.setExpiresAt(request.expiresAt());
        key.setEnabled(true);
        key.setDatasourceIds(request.datasourceIds() == null ? Set.of() : new HashSet<>(request.datasourceIds()));
        keys.save(key);
        audit.record("API_KEY_CREATE", "SUCCESS", "API_KEY", key.getId(), null,
                "name=" + key.getName() + " role=" + role + " tenant=" + tenant);
        return new ApiKeyDtos.Created(view(key), plaintext);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDtos.View> list() {
        requireAdmin();
        Principal principal = AuthContext.currentOrSystem();
        List<ApiKey> items = isGlobalAdmin(principal)
                ? keys.findAllByOrderByCreatedAtDesc()
                : keys.findByTenantIdOrderByCreatedAtDesc(principal.tenantId());
        return items.stream().map(this::view).toList();
    }

    @Transactional
    public void revoke(UUID id) {
        requireAdmin();
        ApiKey key = keys.findById(id).orElseThrow(() -> ApiException.notFound("API Key 不存在: " + id));
        Principal principal = AuthContext.currentOrSystem();
        if (!isGlobalAdmin(principal) && !principal.tenantId().equals(key.getTenantId())) {
            throw ApiException.accessDenied("不能跨租户撤销 API Key");
        }
        key.setEnabled(false);
        keys.save(key);
        audit.record("API_KEY_REVOKE", "SUCCESS", "API_KEY", key.getId(), null,
                "name=" + key.getName() + " prefix=" + key.getKeyPrefix());
    }

    @Transactional
    public Principal authenticate(String plaintext) {
        if (plaintext == null || !plaintext.startsWith("da_")) {
            return null;
        }
        String[] parts = plaintext.split("_", 3);
        if (parts.length != 3) {
            return null;
        }
        ApiKey key = keys.findByKeyPrefix(parts[1]).orElse(null);
        if (key == null || !key.isEnabled()
                || (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now()))) {
            return null;
        }
        byte[] actual = hash(plaintext).getBytes(StandardCharsets.UTF_8);
        byte[] expected = key.getKeyHash().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(actual, expected)) {
            return null;
        }
        key.setLastUsedAt(Instant.now());
        keys.save(key);
        return new Principal("key:" + key.getName(), key.getTenantId(), key.getRole(),
                key.getId(), Set.copyOf(key.getDatasourceIds()));
    }

    public ApiKeyDtos.Me me() {
        Principal principal = AuthContext.currentOrSystem();
        return new ApiKeyDtos.Me(principal.userId(), principal.tenantId(), principal.role(),
                principal.apiKeyId() == null ? null : principal.apiKeyId().toString(),
                principal.datasourceIds().stream().toList());
    }

    private ApiKeyDtos.View view(ApiKey key) {
        return new ApiKeyDtos.View(key.getId(), key.getName(), key.getKeyPrefix(), key.getRole(),
                key.getTenantId(), key.isEnabled(), key.getExpiresAt(), key.getLastUsedAt(),
                key.getDatasourceIds().stream().toList(), key.getCreatedAt());
    }

    private void requireAdmin() {
        if (!AuthContext.currentOrSystem().isAdmin()) {
            throw ApiException.accessDenied("需要 ADMIN 角色");
        }
    }

    private static boolean isGlobalAdmin(Principal principal) {
        return principal.isAdmin() && ("demo".equals(principal.userId()) || "system".equals(principal.userId()));
    }

    private static String normalizeRole(String role) {
        String normalized = role == null ? Principal.READ : role.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(Principal.READ, Principal.EXECUTE, Principal.ADMIN).contains(normalized)) {
            throw ApiException.badRequest("role 必须是 READ / EXECUTE / ADMIN");
        }
        return normalized;
    }

    private String hash(String plaintext) {
        try {
            String pepper = properties.getSecurity().getSecretKey();
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((pepper + ":" + plaintext).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return java.util.HexFormat.of().formatHex(value);
    }
}
