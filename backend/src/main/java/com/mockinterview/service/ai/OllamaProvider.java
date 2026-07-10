package com.mockinterview.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Ollama local models (http://localhost:11434) — llama3, qwen3, mistral, deepseek-r1.
 * No API cost. Disabled by default; enable with app.ai.ollama.enabled=true and a locally
 * running Ollama daemon with the chosen model pulled. Second fallback.
 */
@Service
public class OllamaProvider extends AbstractLLMProvider {


    @Value("${app.ai.ollama.enabled:false}")
    private boolean enabled;

    @Value("${app.ai.ollama.url:http://localhost:11434}")
    private String baseUrl;

    @Value("${app.ai.ollama.model:llama3}")
    private String model;

    @Value("${app.ai.ollama.timeout-ms:60000}")
    private int timeoutMs;

    private final RestTemplate restTemplate;

    public OllamaProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60_000);
        factory.setReadTimeout(60_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() {
        return "Ollama";
    }

    @Override
    public boolean isHealthy() {
        if (!enabled) return false;
        try {
            restTemplate.getForObject(baseUrl.replaceAll("/+$", "") + "/api/version", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected String generateContent(String prompt) {
        if (!enabled) {
            throw new IllegalStateException("Ollama is not enabled.");
        }
        String url = baseUrl.replaceAll("/+$", "") + "/api/generate";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.7);
        body.put("options", options);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map<?, ?> response = restTemplate.postForObject(url, request, Map.class);
        if (response != null && response.containsKey("response")) {
            return (String) response.get("response");
        }
        throw new IllegalStateException("Failed to parse Ollama response");
    }
}


