package com.dataagent.security;

import com.dataagent.config.DataAgentProperties;
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

    public ApiTokenFilter(DataAgentProperties properties) {
        this.properties = properties;
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
            filterChain.doFilter(request, response);
            return;
        }

        String supplied = request.getHeader("X-API-Key");
        if (supplied == null || supplied.isBlank()) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                supplied = authorization.substring(7).trim();
            }
        }

        if (supplied == null || !MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            String message = "缺少或错误的 API Token。请在 X-API-Key 请求头中传入 DATAAGENT_API_TOKEN。";
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\""
                    + message.replace("\"", "\\\"")
                    + "\",\"path\":\"" + request.getRequestURI().replace("\"", "\\\"")
                    + "\",\"timestamp\":\"" + Instant.now() + "\"}");
            return;
        }

        attachUser(request);
        filterChain.doFilter(request, response);
    }

    private void attachUser(HttpServletRequest request) {
        String header = properties.getSecurity().getUserHeader();
        String user = header == null ? null : request.getHeader(header);
        if (user == null || !user.matches("[A-Za-z0-9._@-]{1,120}")) {
            user = DEFAULT_USER;
        }
        request.setAttribute(USER_ATTRIBUTE, user);
    }
}
