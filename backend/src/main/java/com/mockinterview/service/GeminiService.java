package com.mockinterview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiService {

    @Value("${app.ai.gemini.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public GeminiService() {
        this.restTemplate = new RestTemplate();
    }

    public String askGemini(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return "{\"error\": \"API key not configured\"}";
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));

        // Ask Gemini to return only JSON — no markdown, no prose
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 8192);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(content));
        requestBody.put("generationConfig", generationConfig);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response != null && response.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> firstCandidate = candidates.get(0);
                    Map<String, Object> contentMap = (Map<String, Object>) firstCandidate.get("content");
                    if (contentMap != null) {
                        List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            String text = (String) parts.get(0).get("text");
                            return extractJson(text);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Gemini API Error: " + e.getMessage());
        }

        return "{}";
    }

    /**
     * Robustly extracts a JSON object or array from Gemini's response.
     * Handles cases where Gemini wraps JSON in markdown fences, explanatory text, etc.
     */
    public String extractJson(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim();

        // Step 1: strip markdown code fences (```json ... ``` or ``` ... ```)
        cleaned = stripMarkdownFences(cleaned);

        // Step 2: if the result now starts with [ or {, return it
        if (cleaned.startsWith("[") || cleaned.startsWith("{")) {
            return cleaned.trim();
        }

        // Step 3: try to extract a JSON array from anywhere in the text
        String extracted = extractJsonArray(text);
        if (extracted != null) return extracted;

        // Step 4: try to extract a JSON object from anywhere in the text
        extracted = extractJsonObject(text);
        if (extracted != null) return extracted;

        // Step 5: return whatever we have
        return cleaned;
    }

    private String stripMarkdownFences(String text) {
        String cleaned = text.trim();
        // Handle ```json\n...\n``` or ```\n...\n```
        Pattern fencePattern = Pattern.compile("```(?:json)?\\s*\\n?(.*?)\\n?```", Pattern.DOTALL);
        Matcher m = fencePattern.matcher(cleaned);
        if (m.find()) {
            return m.group(1).trim();
        }
        // Handle starting/ending fences without newlines
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        char prev = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && prev != '\\') inString = !inString;
            if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) return text.substring(start, i + 1).trim();
                }
            }
            prev = c;
        }
        return null;
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        char prev = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && prev != '\\') inString = !inString;
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return text.substring(start, i + 1).trim();
                }
            }
            prev = c;
        }
        return null;
    }
}
