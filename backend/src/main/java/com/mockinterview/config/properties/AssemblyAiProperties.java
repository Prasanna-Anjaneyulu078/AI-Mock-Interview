package com.mockinterview.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bindings for {@code app.ai.assemblyai.*} (speech-to-text). Optional — when the
 * API key is blank the service returns a mock transcription.
 */
@ConfigurationProperties(prefix = "app.ai.assemblyai")
@Validated
public class AssemblyAiProperties {

    private String apiKey = "";
    private int timeoutMs = 30_000;
    private int pollTimeoutMs = 60_000;
    private int maxPolls = 20;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey : "";
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getPollTimeoutMs() {
        return pollTimeoutMs;
    }

    public void setPollTimeoutMs(int pollTimeoutMs) {
        this.pollTimeoutMs = pollTimeoutMs;
    }

    public int getMaxPolls() {
        return maxPolls;
    }

    public void setMaxPolls(int maxPolls) {
        this.maxPolls = maxPolls;
    }
}
