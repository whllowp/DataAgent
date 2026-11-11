package com.dataagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "dataagent")
public class DataAgentProperties {

    private final Security security = new Security();
    private final Cors cors = new Cors();
    private final Demo demo = new Demo();
    private final Query query = new Query();
    private final Semantic semantic = new Semantic();
    private final Benchmark benchmark = new Benchmark();
    private final Pii pii = new Pii();
    private final Redis redis = new Redis();
    private final Resilience resilience = new Resilience();
    private final Audit audit = new Audit();
    private final DolphinScheduler dolphinscheduler = new DolphinScheduler();

    public Security getSecurity() {
        return security;
    }

    public Cors getCors() {
        return cors;
    }

    public Demo getDemo() {
        return demo;
    }

    public Query getQuery() {
        return query;
    }

    public Semantic getSemantic() {
        return semantic;
    }

    public Benchmark getBenchmark() {
        return benchmark;
    }

    public Pii getPii() {
        return pii;
    }

    public Redis getRedis() {
        return redis;
    }

    public Resilience getResilience() {
        return resilience;
    }

    public Audit getAudit() {
        return audit;
    }

    public DolphinScheduler getDolphinscheduler() {
        return dolphinscheduler;
    }

    public static class DolphinScheduler {
        private boolean enabled = false;
        private String baseUrl;
        private String token;
        private Long projectCode;
        private String projectName;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Long getProjectCode() {
            return projectCode;
        }

        public void setProjectCode(Long projectCode) {
            this.projectCode = projectCode;
        }

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }
    }

    public static class Audit {
        private int retentionDays = 90;
        private String exportDir = "./data/audit-exports";
        private String siemWebhook;
        private String objectStoreUrl;
        private String objectStoreToken;

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public String getExportDir() {
            return exportDir;
        }

        public void setExportDir(String exportDir) {
            this.exportDir = exportDir;
        }

        public String getSiemWebhook() {
            return siemWebhook;
        }

        public void setSiemWebhook(String siemWebhook) {
            this.siemWebhook = siemWebhook;
        }

        public String getObjectStoreUrl() {
            return objectStoreUrl;
        }

        public void setObjectStoreUrl(String objectStoreUrl) {
            this.objectStoreUrl = objectStoreUrl;
        }

        public String getObjectStoreToken() {
            return objectStoreToken;
        }

        public void setObjectStoreToken(String objectStoreToken) {
            this.objectStoreToken = objectStoreToken;
        }
    }

    public static class Resilience {
        private int failureThreshold = 5;
        private long openMs = 30_000;
        private long slowThresholdMs = 5_000;
        private long slowCooldownMs = 60_000;

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public long getOpenMs() {
            return openMs;
        }

        public void setOpenMs(long openMs) {
            this.openMs = openMs;
        }

        public long getSlowThresholdMs() {
            return slowThresholdMs;
        }

        public void setSlowThresholdMs(long slowThresholdMs) {
            this.slowThresholdMs = slowThresholdMs;
        }

        public long getSlowCooldownMs() {
            return slowCooldownMs;
        }

        public void setSlowCooldownMs(long slowCooldownMs) {
            this.slowCooldownMs = slowCooldownMs;
        }
    }

    public static class Redis {
        private boolean enabled = false;
        private String keyPrefix = "dataagent";
        private long quotaTtlSeconds = 120;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public long getQuotaTtlSeconds() {
            return quotaTtlSeconds;
        }

        public void setQuotaTtlSeconds(long quotaTtlSeconds) {
            this.quotaTtlSeconds = quotaTtlSeconds;
        }
    }

    public static class Pii {
        private boolean enabled = true;
        private boolean maskEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isMaskEnabled() {
            return maskEnabled;
        }

        public void setMaskEnabled(boolean maskEnabled) {
            this.maskEnabled = maskEnabled;
        }
    }

    public static class Security {
        private String apiToken = "dataagent-dev-token";
        private String userHeader = "X-User-Id";
        private String secretKey = "dataagent-dev-secret-change-me";

        public String getApiToken() {
            return apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }

        public String getUserHeader() {
            return userHeader;
        }

        public void setUserHeader(String userHeader) {
            this.userHeader = userHeader;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Demo {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Query {
        private int defaultMaxRows = 500;
        private int hardMaxRows = 5000;
        private int defaultTimeoutSeconds = 15;
        private int hardTimeoutSeconds = 60;
        private int maxSqlLength = 100_000;
        private int maxCellChars = 20_000;
        private long autoPlanThresholdMs = 500;
        private boolean cacheEnabled = true;
        private long cacheTtlSeconds = 30;
        private int cacheMaxEntries = 200;
        private int maxConcurrentQueries = 16;
        private int maxConcurrentPerDatasource = 4;

        public int getDefaultMaxRows() {
            return defaultMaxRows;
        }

        public void setDefaultMaxRows(int defaultMaxRows) {
            this.defaultMaxRows = defaultMaxRows;
        }

        public int getHardMaxRows() {
            return hardMaxRows;
        }

        public void setHardMaxRows(int hardMaxRows) {
            this.hardMaxRows = hardMaxRows;
        }

        public int getDefaultTimeoutSeconds() {
            return defaultTimeoutSeconds;
        }

        public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) {
            this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        }

        public int getHardTimeoutSeconds() {
            return hardTimeoutSeconds;
        }

        public void setHardTimeoutSeconds(int hardTimeoutSeconds) {
            this.hardTimeoutSeconds = hardTimeoutSeconds;
        }

        public int getMaxSqlLength() {
            return maxSqlLength;
        }

        public void setMaxSqlLength(int maxSqlLength) {
            this.maxSqlLength = maxSqlLength;
        }

        public int getMaxCellChars() {
            return maxCellChars;
        }

        public void setMaxCellChars(int maxCellChars) {
            this.maxCellChars = maxCellChars;
        }

        public long getAutoPlanThresholdMs() {
            return autoPlanThresholdMs;
        }

        public void setAutoPlanThresholdMs(long autoPlanThresholdMs) {
            this.autoPlanThresholdMs = autoPlanThresholdMs;
        }

        public boolean isCacheEnabled() {
            return cacheEnabled;
        }

        public void setCacheEnabled(boolean cacheEnabled) {
            this.cacheEnabled = cacheEnabled;
        }

        public long getCacheTtlSeconds() {
            return cacheTtlSeconds;
        }

        public void setCacheTtlSeconds(long cacheTtlSeconds) {
            this.cacheTtlSeconds = cacheTtlSeconds;
        }

        public int getCacheMaxEntries() {
            return cacheMaxEntries;
        }

        public void setCacheMaxEntries(int cacheMaxEntries) {
            this.cacheMaxEntries = cacheMaxEntries;
        }

        public int getMaxConcurrentQueries() {
            return maxConcurrentQueries;
        }

        public void setMaxConcurrentQueries(int maxConcurrentQueries) {
            this.maxConcurrentQueries = maxConcurrentQueries;
        }

        public int getMaxConcurrentPerDatasource() {
            return maxConcurrentPerDatasource;
        }

        public void setMaxConcurrentPerDatasource(int maxConcurrentPerDatasource) {
            this.maxConcurrentPerDatasource = maxConcurrentPerDatasource;
        }
    }

    public static class Semantic {
        private boolean syncEnabled = true;
        private long syncIntervalMs = 1_800_000;
        private long syncInitialDelayMs = 120_000;

        public boolean isSyncEnabled() {
            return syncEnabled;
        }

        public void setSyncEnabled(boolean syncEnabled) {
            this.syncEnabled = syncEnabled;
        }

        public long getSyncIntervalMs() {
            return syncIntervalMs;
        }

        public void setSyncIntervalMs(long syncIntervalMs) {
            this.syncIntervalMs = syncIntervalMs;
        }

        public long getSyncInitialDelayMs() {
            return syncInitialDelayMs;
        }

        public void setSyncInitialDelayMs(long syncInitialDelayMs) {
            this.syncInitialDelayMs = syncInitialDelayMs;
        }
    }

    public static class Benchmark {
        private boolean syncEnabled = true;
        private long syncIntervalMs = 3_600_000;
        private long syncInitialDelayMs = 300_000;

        public boolean isSyncEnabled() {
            return syncEnabled;
        }

        public void setSyncEnabled(boolean syncEnabled) {
            this.syncEnabled = syncEnabled;
        }

        public long getSyncIntervalMs() {
            return syncIntervalMs;
        }

        public void setSyncIntervalMs(long syncIntervalMs) {
            this.syncIntervalMs = syncIntervalMs;
        }

        public long getSyncInitialDelayMs() {
            return syncInitialDelayMs;
        }

        public void setSyncInitialDelayMs(long syncInitialDelayMs) {
            this.syncInitialDelayMs = syncInitialDelayMs;
        }
    }
}
