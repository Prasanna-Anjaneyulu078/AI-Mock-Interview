package com.mockinterview.service;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;


@Service
public class SpeechToTextService {

    @Value("${app.ai.assemblyai.api-key}")
    private String apiKey;

    private final org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

    public String transcribeAudio(org.springframework.web.multipart.MultipartFile audio) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "Mock transcribed text since AssemblyAI key is missing.";
        }

        try {
            // 1. Upload audio
            org.springframework.http.HttpHeaders uploadHeaders = new org.springframework.http.HttpHeaders();
            uploadHeaders.set("Authorization", apiKey);
            uploadHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
            
            org.springframework.http.HttpEntity<byte[]> uploadEntity = new org.springframework.http.HttpEntity<>(audio.getBytes(), uploadHeaders);
            org.springframework.http.ResponseEntity<java.util.Map<String, Object>> uploadResponse = restTemplate.postForEntity(
                    "https://api.assemblyai.com/v2/upload", uploadEntity, (Class<java.util.Map<String, Object>>) (Class<?>) java.util.Map.class);
            
            String uploadUrl = (String) uploadResponse.getBody().get("upload_url");

            // 2. Submit transcript job
            org.springframework.http.HttpHeaders transcriptHeaders = new org.springframework.http.HttpHeaders();
            transcriptHeaders.set("Authorization", apiKey);
            transcriptHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            
            java.util.Map<String, String> transcriptRequest = new java.util.HashMap<>();
            transcriptRequest.put("audio_url", uploadUrl);
            
            org.springframework.http.HttpEntity<java.util.Map<String, String>> transcriptEntity = new org.springframework.http.HttpEntity<>(transcriptRequest, transcriptHeaders);
            org.springframework.http.ResponseEntity<java.util.Map<String, Object>> transcriptResponse = restTemplate.postForEntity(
                    "https://api.assemblyai.com/v2/transcript", transcriptEntity, (Class<java.util.Map<String, Object>>) (Class<?>) java.util.Map.class);
            
            String transcriptId = (String) transcriptResponse.getBody().get("id");

            // 3. Poll
            String transcriptStatus = "queued";
            java.util.Map<String, Object> resultBody = null;
            while ("queued".equals(transcriptStatus) || "processing".equals(transcriptStatus)) {
                Thread.sleep(3000); // Wait 3 seconds
                
                org.springframework.http.HttpEntity<Void> pollEntity = new org.springframework.http.HttpEntity<>(transcriptHeaders);
                org.springframework.http.ResponseEntity<java.util.Map<String, Object>> pollResponse = restTemplate.exchange(
                        "https://api.assemblyai.com/v2/transcript/" + transcriptId,
                        org.springframework.http.HttpMethod.GET,
                        pollEntity,
                        (Class<java.util.Map<String, Object>>) (Class<?>) java.util.Map.class);
                
                resultBody = pollResponse.getBody();
                transcriptStatus = (String) resultBody.get("status");
            }

            if ("error".equals(transcriptStatus)) {
                return "[No speech detected or transcription failed]";
            }

            return resultBody != null ? (String) resultBody.get("text") : "[No speech detected]";
        } catch (Exception e) {
            System.err.println("SpeechToTextService Error: " + e.getMessage());
            return "Error transcribing audio.";
        }
    }
}
