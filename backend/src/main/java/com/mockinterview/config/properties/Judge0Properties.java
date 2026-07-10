package com.mockinterview.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bindings for {@code app.ai.judge0.*} (code execution). Optional — when the URL is
 * blank the service returns {@code null} and the caller falls back to AI code evaluation.
 */
@ConfigurationProperties(prefix = "app.ai.judge0")
@Validated
public class Judge0Properties {

    private String apiKey = "";
    private String url = "";
    private int timeoutMs = 30_000;

    public boolean isConfigured() {
        if (url == null || url.isBlank()) return false;
        if (url.startsWith("http://")) return false;
        try {
            java.net.URI.create(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey : "";
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        if (url == null || url.isBlank()) {
            this.url = "";
        } else {
            String temp = url.trim();
            this.url = temp.startsWith("http") ? temp : "https://" + temp;
        }
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
