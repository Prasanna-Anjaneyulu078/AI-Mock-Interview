package com.mockinterview.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bindings for {@code app.ai.gemini.*}. The API key is optional: when blank the
 * {@link com.mockinterview.service.GeminiService} disables itself gracefully and the
 * interview falls back to static questions rather than crashing.
 */
@ConfigurationProperties(prefix = "app.ai.gemini")
@Validated
public class GeminiProperties {

    private String apiKey = "";
    private String model = "gemini-2.5-flash";
    private int timeoutMs = 30_000;

    public boolean isConfigured() {
        return apiKey != null
            && !apiKey.isBlank()
            && apiKey.startsWith("AIza")
            && apiKey.length() > 30;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey : "";
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
