package com.mockinterview.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bindings for {@code app.ai.ollama.*}. Disabled by default; set {@code enabled=true} and
 * run a local Ollama daemon (with the chosen model pulled) to activate this provider.
 */
@ConfigurationProperties(prefix = "app.ai.ollama")
@Validated
public class OllamaProperties {

    private boolean enabled = false;
    private String url = "http://localhost:11434";
    private String model = "llama3";
    private int timeoutMs = 30_000;

    public boolean isConfigured() {
        return enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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
