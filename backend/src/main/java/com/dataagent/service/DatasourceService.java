package com.dataagent.service;

import com.dataagent.config.DataAgentProperties;
import com.dataagent.domain.Datasource;
import com.dataagent.domain.DbDialect;
import com.dataagent.repository.DatasourceRepository;
import com.dataagent.security.SecretCipher;
import com.dataagent.web.ApiException;
import com.dataagent.web.dto.DatasourceDtos;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DatasourceService {

    private static final Logger log = LoggerFactory.getLogger(DatasourceService.class);
    private static final List<String> ALLOWED_URL_PREFIXES = List.of(
            "jdbc:postgresql:", "jdbc:mysql:", "jdbc:mariadb:", "jdbc:h2:");

    private final DatasourceRepository repository;
    private final SecretCipher cipher;
    private final DataAgentProperties properties;
    private final Map<UUID, HikariDataSource> pools = new ConcurrentHashMap<>();

    public DatasourceService(DatasourceRepository repository, SecretCipher cipher, DataAgentProperties properties) {
        this.repository = repository;
        this.cipher = cipher;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<DatasourceDtos.DatasourceView> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Datasource::getCreatedAt))
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public DatasourceDtos.DatasourceView getView(UUID id) {
        return toView(getRequired(id));
    }

    @Transactional(readOnly = true)
    public Datasource getRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("数据源不存在: " + id));
    }

    @Transactional
    public DatasourceDtos.DatasourceView create(DatasourceDtos.DatasourceRequest request) {
        validateUrl(request.jdbcUrl());
        DbDialect dialect = request.dialect() == null || request.dialect().isBlank()
                ? DbDialect.infer(request.jdbcUrl())
                : DbDialect.from(request.dialect());

        Datasource datasource = new Datasource();
        datasource.setName(request.name().trim());
        datasource.setDialect(dialect.name());
        datasource.setJdbcUrl(request.jdbcUrl().trim());
        datasource.setUsername(blankToNull(request.username()));
        datasource.setPasswordCipher(cipher.encrypt(request.password()));
        datasource.setReadOnly(request.readOnly() == null || request.readOnly());
        datasource.setMaxRows(clamp(request.maxRows(), 1, properties.getQuery().getHardMaxRows(),
                properties.getQuery().getDefaultMaxRows()));
        datasource.setQueryTimeoutSeconds(clamp(request.queryTimeoutSeconds(), 1,
                properties.getQuery().getHardTimeoutSeconds(), properties.getQuery().getDefaultTimeoutSeconds()));
        datasource.setDescription(blankToNull(request.description()));
        repository.save(datasource);
        log.info("Registered datasource {} ({}, {})", datasource.getName(), dialect, datasource.getId());
        return toView(datasource);
    }

    @Transactional
    public DatasourceDtos.DatasourceView update(UUID id, DatasourceDtos.DatasourceRequest request) {
        validateUrl(request.jdbcUrl());
        Datasource datasource = getRequired(id);
        DbDialect dialect = request.dialect() == null || request.dialect().isBlank()
                ? DbDialect.infer(request.jdbcUrl())
                : DbDialect.from(request.dialect());
        datasource.setName(request.name().trim());
        datasource.setDialect(dialect.name());
        datasource.setJdbcUrl(request.jdbcUrl().trim());
        datasource.setUsername(blankToNull(request.username()));
        if (request.password() != null && !request.password().isBlank()) {
            datasource.setPasswordCipher(cipher.encrypt(request.password()));
        }
        datasource.setReadOnly(request.readOnly() == null || request.readOnly());
        datasource.setMaxRows(clamp(request.maxRows(), 1, properties.getQuery().getHardMaxRows(),
                properties.getQuery().getDefaultMaxRows()));
        datasource.setQueryTimeoutSeconds(clamp(request.queryTimeoutSeconds(), 1,
                properties.getQuery().getHardTimeoutSeconds(), properties.getQuery().getDefaultTimeoutSeconds()));
        datasource.setDescription(blankToNull(request.description()));
        repository.save(datasource);
        evict(id);
        return toView(datasource);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw ApiException.notFound("数据源不存在: " + id);
        }
        repository.deleteById(id);
        evict(id);
    }

    @Transactional(readOnly = true)
    public DatasourceDtos.TestResult test(UUID id) {
        Datasource datasource = getRequired(id);
        long start = System.nanoTime();
        try (HikariDataSource probe = buildPool(datasource, 1)) {
            try (Connection connection = probe.getConnection()) {
                DatabaseMetaData meta = connection.getMetaData();
                return new DatasourceDtos.TestResult(
                        true,
                        Duration.ofNanos(System.nanoTime() - start).toMillis(),
                        "连接成功",
                        meta.getDatabaseProductName(),
                        meta.getDatabaseProductVersion());
            }
        } catch (Exception e) {
            return new DatasourceDtos.TestResult(
                    false,
                    Duration.ofNanos(System.nanoTime() - start).toMillis(),
                    rootMessage(e),
                    null,
                    null);
        }
    }

    public DataSource dataSourceFor(UUID id) {
        Datasource datasource = getRequired(id);
        return pools.computeIfAbsent(id, key -> {
            log.info("Creating JDBC pool for datasource {} ({})", datasource.getName(), id);
            return buildPool(datasource, 5);
        });
    }

    public String passwordOf(Datasource datasource) {
        return cipher.decrypt(datasource.getPasswordCipher());
    }

    private HikariDataSource buildPool(Datasource datasource, int maxPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(datasource.getJdbcUrl());
        config.setUsername(datasource.getUsername());
        config.setPassword(cipher.decrypt(datasource.getPasswordCipher()));
        config.setPoolName("dataagent-ds-" + datasource.getId().toString().substring(0, 8));
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(3_000);
        config.setIdleTimeout(60_000);
        config.setMaxLifetime(600_000);
        config.setReadOnly(datasource.isReadOnly());
        config.setInitializationFailTimeout(-1);
        config.addDataSourceProperty("ApplicationName", "DataAgent");
        return new HikariDataSource(config);
    }

    private void validateUrl(String jdbcUrl) {
        String url = jdbcUrl == null ? "" : jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (ALLOWED_URL_PREFIXES.stream().noneMatch(url::startsWith)) {
            throw ApiException.badRequest("仅支持 PostgreSQL（含 openGauss/DWS）/ MySQL / H2 的 JDBC URL");
        }
        if (url.startsWith("jdbc:h2:") && (url.contains("init=") || url.contains("runscript"))) {
            throw ApiException.badRequest("H2 URL 不允许使用 INIT / RUNSCRIPT 参数");
        }
        if (url.contains("allowloadlocalinfile=true") || url.contains("autodeserialize=true")) {
            throw ApiException.badRequest("JDBC URL 包含不安全的反序列化/文件读取参数");
        }
    }

    private DatasourceDtos.DatasourceView toView(Datasource datasource) {
        return new DatasourceDtos.DatasourceView(
                datasource.getId(),
                datasource.getName(),
                datasource.getDialect(),
                datasource.getJdbcUrl(),
                datasource.getUsername(),
                datasource.isReadOnly(),
                datasource.getMaxRows(),
                datasource.getQueryTimeoutSeconds(),
                datasource.getDescription(),
                datasource.getPasswordCipher() != null && !datasource.getPasswordCipher().isBlank(),
                datasource.getCreatedAt());
    }

    private void evict(UUID id) {
        HikariDataSource pool = pools.remove(id);
        if (pool != null) {
            pool.close();
        }
    }

    @PreDestroy
    void shutdown() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }
}
