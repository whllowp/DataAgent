package com.dataagent.security;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Set;

public class ApiTokenFilter extends OncePerRequestFilter {

    public static final String USER_ATTRIBUTE = "dataagent.userId";
    private static final Set<String> OPEN_PATHS = Set.of("/api/health");
    private static final String DEFAULT_USER = "demo";

    private final DataAgentProperties properties;
    private final ApiKeyService apiKeys;

    public ApiTokenFilter(DataAgentProperties properties, ApiKeyService apiKeys) {
        this.properties = properties;
        this.apiKeys = apiKeys;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return OPEN_PATHS.contains(path) || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String expected = properties.getSecurity().getApiToken();
        if (expected == null || expected.isBlank()) {
            attachUser(request);
            try {
                filterChain.doFilter(request, response);
            } finally {
                AuthContext.clear();
            }
            return;
        }

        String supplied = request.getHeader("X-API-Key");
        if (supplied == null || supplied.isBlank()) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                supplied = authorization.substring(7).trim();
            }
        }

        Principal principal = null;
        if (supplied != null && supplied.startsWith("da_")) {
            principal = apiKeys.authenticate(supplied);
            if (principal == null) {
                unauthorized(response, request, "API Key 无效、已撤销或已过期。");
                return;
            }
        } else if (supplied != null && MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            principal = Principal.legacyAdmin();
        }

        if (principal == null) {
            unauthorized(response, request,
                    "缺少或错误的 API Token。请在 X-API-Key 请求头中传入 DATAAGENT_API_TOKEN 或 scoped API Key。");
            return;
        }

        AuthContext.set(principal);
        request.setAttribute(USER_ATTRIBUTE, principal.userId());
        try {
            if (!authorized(request, principal)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"当前 API Key 无权执行该操作\",\"path\":\""
                        + request.getRequestURI().replace("\"", "\\\"")
                        + "\",\"timestamp\":\"" + Instant.now() + "\"}");
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private boolean authorized(HttpServletRequest request, Principal principal) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith("/api/api-keys") || path.startsWith("/api/audit")) {
            return principal.isAdmin();
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if (path.startsWith("/api/datasources") && !"GET".equalsIgnoreCase(method)) {
            return principal.isAdmin();
        }
        if (path.startsWith("/api/sql/")) {
            return principal.canExecute();
        }
        if (path.startsWith("/api/benchmarks") && "POST".equalsIgnoreCase(method)) {
            return principal.canExecute();
        }
        if (path.startsWith("/api/semantic/sync") && "POST".equalsIgnoreCase(method)) {
            return principal.isAdmin();
        }
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return true;
        }
        return principal.canExecute();
    }

    private void unauthorized(HttpServletResponse response, HttpServletRequest request,
                              String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\""
                + message.replace("\"", "\\\"")
                + "\",\"path\":\"" + request.getRequestURI().replace("\"", "\\\"")
                + "\",\"timestamp\":\"" + Instant.now() + "\"}");
    }

    private void attachUser(HttpServletRequest request) {
        String header = properties.getSecurity().getUserHeader();
        String user = header == null ? null : request.getHeader(header);
        if (user == null || !user.matches("[A-Za-z0-9._@-]{1,120}")) {
            user = DEFAULT_USER;
        }
        request.setAttribute(USER_ATTRIBUTE, user);
        AuthContext.set(Principal.legacyAdmin());
    }
}
