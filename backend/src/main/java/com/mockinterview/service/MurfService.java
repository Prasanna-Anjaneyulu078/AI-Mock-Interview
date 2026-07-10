package com.mockinterview.service;

import com.mockinterview.dto.MurfVoiceDTO;
import com.mockinterview.util.ResilienceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Real Murf AI text-to-speech client.
 *
 * <p>Calls {@code POST https://api.murf.ai/v1/speech/generate} with the {@code api-key}
 * header and returns the generated audio URL ({@code audioFile}) or, when Murf returns
 * only base64, a {@code data:} URL. Designed to degrade gracefully: when the API key is
 * missing or the service is unavailable, it returns {@code null} so callers can continue
 * without audio rather than failing the whole interview flow.
 *
 * <p>Resilience: up to {@code maxRetries} attempts with exponential backoff. 4xx
 * (auth/invalid) are NOT retried; 5xx and timeouts ARE retried.
 */
@Service
public class MurfService {

    private static final String MURF_URL = "https://api.murf.ai/v1/speech/generate";
    private static final String MURF_VOICES_URL = "https://api.murf.ai/v1/speech/voices";

    private static final Logger log = LoggerFactory.getLogger(MurfService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String defaultVoiceId;
    private final String defaultStyle;
    private final int timeoutMs;
    private final int maxRetries;
    private final ResilienceUtil.CircuitBreaker circuitBreaker =
            new ResilienceUtil.CircuitBreaker(5, 30_000);

    public MurfService(@Qualifier("murfRestTemplate") RestTemplate restTemplate,
                       @Value("${app.ai.murf.api-key}") String apiKey,
                       @Value("${app.ai.murf.default-voice:en-US-natalie}") String defaultVoiceId,
                       @Value("${app.ai.murf.default-style:Conversational}") String defaultStyle,
                       @Value("${app.ai.murf.timeout-ms:10000}") int timeoutMs,
                       @Value("${app.ai.murf.max-retries:3}") int maxRetries,
                       io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.circuitBreaker.setMetrics(meterRegistry, "murf");
        this.defaultVoiceId = defaultVoiceId;
        this.defaultStyle = defaultStyle;
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
    }

    public String generateSpeech(String text) {
        return generateSpeech(text, MurfVoiceOptions.defaults());
    }

    public String generateSpeech(String text, MurfVoiceOptions options) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Murf API key not configured; skipping text-to-speech (UI continues silently).");
            return null;
        }
        if (circuitBreaker.isOpen()) {
            log.warn("Murf circuit breaker OPEN — skipping text-to-speech (UI continues silently).");
            return null;
        }
        if (text == null || text.isBlank()) {
            return null;
        }

        // Voice Optimization: Inject SSML for human-like pauses and emphasis
        String processedText = injectSSML(text);

        Map<String, Object> body = new HashMap<>();
        body.put("text", processedText);
        body.put("voiceId", options.getVoiceId() != null ? options.getVoiceId() : defaultVoiceId);
        body.put("format", "MP3");
        body.put("modelVersion", "GEN2");
        if (options.getStyle() != null) {
            body.put("style", options.getStyle());
        } else if (defaultStyle != null && !defaultStyle.isBlank()) {
            body.put("style", defaultStyle);
        }
        if (options.getRate() != null) body.put("rate", options.getRate());
        if (options.getPitch() != null) body.put("pitch", options.getPitch());
        // Return a URL (efficient) rather than embedding base64 in API responses / DB.
        body.put("encodeAsBase64", false);

        long backoff = 500;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("api-key", apiKey);
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

                var response = restTemplate.postForEntity(MURF_URL, entity, Map.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> respBody = (Map<String, Object>) response.getBody();
                    Object audioFile = respBody.get("audioFile");
                    if (audioFile instanceof String s && !s.isBlank()) {
                        circuitBreaker.onSuccess();
                        return s;
                    }
                    Object encoded = respBody.get("encodedAudio");
                    if (encoded instanceof String e && !e.isBlank()) {
                        circuitBreaker.onSuccess();
                        return "data:audio/mp3;base64," + e;
                    }
                    log.warn("Murf returned success but no audio content (attempt {}/{}).", attempt, maxRetries);
                } else {
                    log.warn("Murf returned non-success status {} (attempt {}/{}).", response.getStatusCode(), attempt, maxRetries);
                }
            } catch (HttpStatusCodeException ex) {
                int code = ex.getStatusCode().value();
                if (code == HttpStatus.UNAUTHORIZED.value()
                        || code == HttpStatus.FORBIDDEN.value()
                        || code == HttpStatus.BAD_REQUEST.value()) {
                    log.error("Murf request rejected with HTTP {}: {}. Not retrying.", code, ex.getResponseBodyAsString());
                    return null;
                }
                log.warn("Murf attempt {}/{} failed with HTTP {}: {}", attempt, maxRetries, code, ex.getStatusText());
            } catch (ResourceAccessException ex) {
                log.warn("Murf attempt {}/{} timed out/unreachable ({}ms): {}", attempt, maxRetries, timeoutMs, ex.getMessage());
            } catch (Exception ex) {
                log.warn("Murf attempt {}/{} error: {}", attempt, maxRetries, ex.getMessage());
            }

            if (attempt < maxRetries) {
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                backoff = Math.min(backoff * 2, 8000);
            }
        }
        log.error("Murf text-to-speech failed after {} attempts for text: \"{}\"", maxRetries, truncate(text));
        circuitBreaker.onFailure();
        return null;
    }

    private String truncate(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }

    /**
     * Fetch the list of available Murf voices for the setup UI picker.
     * Returns an empty list when the key is missing or the call fails (graceful).
     */
    public List<MurfVoiceDTO> getVoices() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Murf API key not configured; cannot fetch voice list.");
            return List.of();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("api-key", apiKey);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            var response = restTemplate.exchange(MURF_VOICES_URL, HttpMethod.GET, entity, List.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<?> raw = response.getBody();
                @SuppressWarnings("unchecked")
                List<MurfVoiceDTO> list = raw.stream()
                        .filter(m -> m instanceof Map)
                        .map(m -> toVoiceDto((Map<String, Object>) m))
                        .collect(Collectors.toList());
                return list;
            }
            log.warn("Murf voices endpoint returned status {}", response.getStatusCode());
        } catch (Exception ex) {
            log.warn("Failed to fetch Murf voices: {}", ex.getMessage());
        }
        return List.of();
    }

    private MurfVoiceDTO toVoiceDto(Map<String, Object> m) {
        return MurfVoiceDTO.builder()
                .voiceId(asString(m.get("voiceId")))
                .name(asString(m.get("voiceName") != null ? m.get("voiceName") : m.get("displayName")))
                .gender(asString(m.get("gender")))
                .locale(asString(m.get("locale")))
                .language(asString(m.get("language")))
                .styles(extractStyles(m.get("styles")))
                .build();
    }

    private String getAuthErrorMessage(String message) {
        if (message.contains("Invalid API Key")) {
            return "Murf AI rejected the API key. Please check your MURF_API_KEY environment variable.";
        }
        return "Murf AI authentication failed: " + message;
    }

    /**
     * Injects SSML tags to improve voice naturalness.
     * Replaces sentence endings (.!?) with slight pauses, and wraps the text in <speak>.
     */
    private String injectSSML(String originalText) {
        if (originalText == null || originalText.isBlank()) return originalText;
        
        String escaped = originalText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        // Add 500ms pauses at sentence boundaries
        String withBreaks = escaped.replaceAll("([.?!]+)\\s+", "$1 <break time=\"500ms\"/> ");

        // Basic capitalization emphasis (e.g. "Spring Boot")
        // Not perfectly robust for all acronyms, but adds some vocal dynamics
        withBreaks = withBreaks.replaceAll("\\b([A-Z][a-zA-Z]*\\s[A-Z][a-zA-Z]*)\\b", "<emphasis level=\"moderate\">$1</emphasis>");

        return "<speak>" + withBreaks + "</speak>";
    }

    private List<String> extractStyles(Object styles) {
        if (styles instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
