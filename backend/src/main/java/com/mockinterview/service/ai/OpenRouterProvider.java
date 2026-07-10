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
 * OpenRouter (https://openrouter.ai) — free-tier OpenAI-compatible chat models
 * (deepseek/deepseek-r1, deepseek/deepseek-chat, qwen/qwen3, mistralai/mistral-small).
 * Primary provider in the default chain.
 */
@Service
public class OpenRouterProvider extends AbstractLLMProvider {


    @Value("${app.ai.openrouter.api-key:}")
    private String apiKey;

    @Value("${app.ai.openrouter.model:deepseek/deepseek-r1}")
    private String model;

    @Value("${app.ai.openrouter.base-url:https://openrouter.ai/api/v1}")
    private String baseUrl;

    @Value("${app.ai.openrouter.timeout-ms:30000}")
    private int timeoutMs;

    private final RestTemplate restTemplate;

    public OpenRouterProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() {
        return "OpenRouter";
    }

    @Override
    public boolean isHealthy() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    protected String generateContent(String prompt) {
        if (!isHealthy()) {
            throw new IllegalStateException("OpenRouter is not configured.");
        }
        String url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", "https://ai-mock-interview.local");
        headers.set("X-Title", "AI Mock Interview Platform");

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
            throw new IllegalStateException("OpenRouter error: " + response.get("error"));
        }
        throw new IllegalStateException("Failed to parse OpenRouter response");
    }
}


