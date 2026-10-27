package com.dataagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "dataagent")
public class DataAgentProperties {

    private final Security security = new Security();
    private final Cors cors = new Cors();
    private final Demo demo = new Demo();
    private final Query query = new Query();

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
    }
}
