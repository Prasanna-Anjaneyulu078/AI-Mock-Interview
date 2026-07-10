package com.mockinterview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * AssemblyAI speech-to-text client.
 *
 * Phase 4 improvements:
 * - Bounded polling loop (max polls configurable; default 20)
 * - Exponential backoff between polls (3s → 5s → 8s, capped at 10s)
 * - Explicit empty-transcript detection
 * - Upload failure handling with detailed logging
 * - Graceful fallback to empty string (not an error marker) on partial failures
 */
@Service
public class SpeechToTextService {

    private static final Logger log = LoggerFactory.getLogger(SpeechToTextService.class);

    private static final String UPLOAD_URL      = "https://api.assemblyai.com/v2/upload";
    private static final String TRANSCRIPT_URL  = "https://api.assemblyai.com/v2/transcript";
    private static final long   BASE_POLL_MS    = 3_000;
    private static final long   MAX_POLL_MS     = 10_000;

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final int maxPolls;

    public SpeechToTextService(@Qualifier("assemblyAiRestTemplate") RestTemplate restTemplate,
                               @Value("${app.ai.assemblyai.api-key:}") String apiKey,
                               @Value("${app.ai.assemblyai.max-polls:20}") int maxPolls) {
        this.restTemplate = restTemplate;
        this.apiKey       = apiKey;
        this.maxPolls     = maxPolls;
    }

    @SuppressWarnings("unchecked")
    public String transcribeAudio(MultipartFile audio) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("AssemblyAI API key not configured — returning empty transcript.");
            return "";
        }

        // ── 1. Upload audio ──────────────────────────────────────────────────────
        String uploadUrl;
        try {
            HttpHeaders uploadHeaders = new HttpHeaders();
            uploadHeaders.set("Authorization", apiKey);
            uploadHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            HttpEntity<byte[]> uploadEntity = new HttpEntity<>(audio.getBytes(), uploadHeaders);
            ResponseEntity<Map<String, Object>> uploadResponse = restTemplate.postForEntity(
                    UPLOAD_URL, uploadEntity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            if (uploadResponse.getBody() == null) {
                log.error("AssemblyAI upload returned null body — cannot transcribe.");
                return "";
            }
            uploadUrl = (String) uploadResponse.getBody().get("upload_url");
            if (uploadUrl == null || uploadUrl.isBlank()) {
                log.error("AssemblyAI upload_url is missing from response.");
                return "";
            }
            log.debug("AssemblyAI upload succeeded: {}", uploadUrl);
        } catch (Exception e) {
            log.error("AssemblyAI audio upload failed: {}", e.getMessage());
            return "";
        }

        // ── 2. Submit transcript job ─────────────────────────────────────────────
        String transcriptId;
        try {
            HttpHeaders transcriptHeaders = new HttpHeaders();
            transcriptHeaders.set("Authorization", apiKey);
            transcriptHeaders.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> transcriptRequest = new HashMap<>();
            transcriptRequest.put("audio_url", uploadUrl);

            HttpEntity<Map<String, String>> transcriptEntity = new HttpEntity<>(transcriptRequest, transcriptHeaders);
            ResponseEntity<Map<String, Object>> transcriptResponse = restTemplate.postForEntity(
                    TRANSCRIPT_URL, transcriptEntity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            if (transcriptResponse.getBody() == null) {
                log.error("AssemblyAI transcript submission returned null body.");
                return "";
            }
            transcriptId = (String) transcriptResponse.getBody().get("id");
            if (transcriptId == null || transcriptId.isBlank()) {
                log.error("AssemblyAI transcript ID missing from submission response.");
                return "";
            }
            log.debug("AssemblyAI transcript job submitted: id={}", transcriptId);
        } catch (Exception e) {
            log.error("AssemblyAI transcript submission failed: {}", e.getMessage());
            return "";
        }

        // ── 3. Bounded polling with exponential backoff ──────────────────────────
        HttpHeaders pollHeaders = new HttpHeaders();
        pollHeaders.set("Authorization", apiKey);
        HttpEntity<Void> pollEntity = new HttpEntity<>(pollHeaders);
        String pollUrl = TRANSCRIPT_URL + "/" + transcriptId;

        long pollDelay = BASE_POLL_MS;
        for (int poll = 0; poll < maxPolls; poll++) {
            try {
                Thread.sleep(pollDelay);
                pollDelay = Math.min(pollDelay + 2_000, MAX_POLL_MS); // backoff: +2s per round, cap 10s
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("AssemblyAI poll interrupted.");
                return "";
            }

            try {
                ResponseEntity<Map<String, Object>> pollResponse = restTemplate.exchange(
                        pollUrl, HttpMethod.GET, pollEntity,
                        (Class<Map<String, Object>>) (Class<?>) Map.class);

                Map<String, Object> resultBody = pollResponse.getBody();
                if (resultBody == null) {
                    log.warn("AssemblyAI poll {} returned null body.", poll + 1);
                    continue;
                }

                String status = (String) resultBody.get("status");
                log.debug("AssemblyAI poll {}/{}: status={}", poll + 1, maxPolls, status);

                if ("completed".equals(status)) {
                    String text = (String) resultBody.get("text");
                    if (text == null || text.isBlank()) {
                        log.info("AssemblyAI transcription completed but returned empty text — treating as skipped.");
                        return "";
                    }
                    log.info("AssemblyAI transcription completed: {} chars", text.length());
                    return text;
                }

                if ("error".equals(status)) {
                    String errorMsg = resultBody.get("error") instanceof String s ? s : "unknown";
                    log.error("AssemblyAI transcription error: {}", errorMsg);
                    return "";
                }

                // "queued" or "processing" — continue polling

            } catch (Exception e) {
                log.warn("AssemblyAI poll {} failed: {}", poll + 1, e.getMessage());
            }
        }

        log.error("AssemblyAI transcript polling timed out after {} polls for id={}", maxPolls, transcriptId);
        return "";
    }
}
