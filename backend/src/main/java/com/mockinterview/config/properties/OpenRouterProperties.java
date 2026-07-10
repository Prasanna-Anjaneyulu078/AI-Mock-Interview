package com.mockinterview.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bindings for {@code app.ai.openrouter.*}. Optional: when the API key is blank the
 * {@link com.mockinterview.ai.OpenRouterProvider} disables itself and the router skips it.
 */
@ConfigurationProperties(prefix = "app.ai.openrouter")
@Validated
public class OpenRouterProperties {

    private String apiKey = "";
    private String model = "deepseek/deepseek-r1";
    private String baseUrl = "https://openrouter.ai/api/v1";
    private int timeoutMs = 30_000;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
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

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
