package com.mockinterview.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq (https://groq.com) — free-tier OpenAI-compatible chat API with fast inference
 * (llama-3.3-70b-versatile, llama-3.1-8b-instant). First fallback.
 */
@Service
public class GroqProvider extends AbstractLLMProvider {


    @Value("${app.ai.groq.api-key:}")
    private String apiKey;

    @Value("${app.ai.groq.model:llama-3.3-70b-versatile}")
    private String model;

    @Value("${app.ai.groq.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    @Value("${app.ai.groq.timeout-ms:30000}")
    private int timeoutMs;

    private final RestTemplate restTemplate;

    public GroqProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() {
        return "Groq";
    }

    @Override
    public boolean isHealthy() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    protected String generateContent(String prompt) {
        if (!isHealthy()) {
            throw new IllegalStateException("Groq is not configured.");
        }
        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("temperature", 0.7);
        body.put("max_tokens", 8192);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        Map<?, ?> response = restTemplate.postForObject(url, request, Map.class);
        return extractResponseText(response);
    }

    @SuppressWarnings("unchecked")
    private String extractResponseText(Map<?, ?> response) {
        if (response != null && response.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null && message.containsKey("content")) {
                    return (String) message.get("content");
                }
            }
        }
        if (response != null && response.containsKey("error")) {
            throw new IllegalStateException("Groq error: " + response.get("error"));
        }
        throw new IllegalStateException("Failed to parse Groq response");
    }
}


