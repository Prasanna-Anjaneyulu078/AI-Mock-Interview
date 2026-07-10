package com.mockinterview.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust extraction of JSON from an LLM / provider response. Models frequently wrap
 * JSON in markdown code fences or explanatory prose; this utility strips fences and
 * pulls out the first balanced object or array. Shared by the callers and the providers.
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    public static String extractJson(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim();
        cleaned = stripMarkdownFences(cleaned);
        if (cleaned.startsWith("[") || cleaned.startsWith("{")) {
            return cleaned.trim();
        }
        String extracted = extractJsonArray(text);
        if (extracted != null) return extracted;
        extracted = extractJsonObject(text);
        if (extracted != null) return extracted;
        return cleaned;
    }

    public static String stripMarkdownFences(String text) {
        String cleaned = text.trim();
        Pattern fencePattern = Pattern.compile("```(?:json)?\s*\n?(.*?)\n?```", Pattern.DOTALL);
        Matcher m = fencePattern.matcher(cleaned);
        if (m.find()) {
            return m.group(1).trim();
        }
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

    public static String extractJsonArray(String text) {
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

    public static String extractJsonObject(String text) {
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

    public static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...(truncated)";
    }
}
