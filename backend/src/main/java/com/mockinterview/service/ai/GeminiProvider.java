package com.mockinterview.service.ai;

import com.mockinterview.config.properties.GeminiProperties;
import com.mockinterview.exception.AIProviderException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiProvider extends AbstractLLMProvider {

    private final GeminiProperties properties;
    private final RestTemplate restTemplate;

    public GeminiProvider(GeminiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() {
        return "Gemini";
    }

    @Override
    public boolean isHealthy() {
        return properties.isConfigured();
    }

    @Override
    protected String generateContent(String prompt) {
        if (!isHealthy()) {
            throw new IllegalStateException("Gemini is not configured.");
        }
        
        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", 
                properties.getModel(), properties.getApiKey());
                
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);
        
        Map<String, Object> partContainer = new HashMap<>();
        partContainer.put("parts", List.of(textPart));

        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(partContainer));

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            Map<?, ?> response = restTemplate.postForObject(url, request, Map.class);
            return extractResponseText(response);
        } catch (HttpStatusCodeException e) {
            String errorCode = "API_ERROR";
            String errorMessage = "Gemini API error: " + e.getStatusText();
            if (e.getStatusCode().value() == 403) {
                errorCode = "ACCESS_DENIED";
                errorMessage = "Access denied or invalid API key.";
            } else if (e.getStatusCode().value() == 429) {
                errorCode = "RATE_LIMIT_EXCEEDED";
                errorMessage = "Too many requests have been sent.";
            }
            throw new AIProviderException("Gemini", e.getStatusCode().value(), errorCode, errorMessage, null);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractResponseText(Map<?, ?> response) {
        if (response != null && response.containsKey("candidates")) {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                if (content != null && content.containsKey("parts")) {
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        return (String) parts.get(0).get("text");
                    }
                }
            }
        }
        throw new IllegalStateException("Failed to parse Gemini response");
    }
}
