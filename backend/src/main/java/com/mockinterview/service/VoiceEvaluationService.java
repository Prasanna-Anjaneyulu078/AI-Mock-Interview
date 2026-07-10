package com.mockinterview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.entity.Answer;
import com.mockinterview.entity.Question;
import com.mockinterview.dto.AnswerRequest;
import com.mockinterview.service.ai.AIProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class VoiceEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(VoiceEvaluationService.class);
    private final AIProvider aiProvider;
    private final ObjectMapper objectMapper;

    public VoiceEvaluationService(AIProvider aiProvider) {
        this.aiProvider = aiProvider;
        this.objectMapper = new ObjectMapper();
    }

    public void evaluateVoiceAnswer(Answer answer, Question question, AnswerRequest request) {
        try {
            String answerText = request.getAnswerText();
            if (answerText == null || answerText.isBlank()) {
                answer.setEvaluationScore(0.0);
                answer.setFeedback("No speech was detected or transcribed.");
                return;
            }

            // Calculate Base Voice Metrics
            if (request.getAudioDurationSeconds() != null && request.getAudioDurationSeconds() > 0) {
                int wordCount = answerText.split("\\s+").length;
                double wpm = (wordCount / (double) request.getAudioDurationSeconds()) * 60.0;
                answer.setSpeakingSpeed(wpm);
                answer.setAudioDuration((double) request.getAudioDurationSeconds());
            }

            if (request.getFillerWordsCount() != null) {
                answer.setFillerWordsCount(request.getFillerWordsCount());
            }

            if (request.getRecordingUrl() != null) {
                answer.setRecordingUrl(request.getRecordingUrl());
            }

            // Prompt AI for Voice Evaluation
            String eval = aiProvider.evaluateAnswer(question.getQuestionText(), answerText, null, null);
            
            String evalJson = extractJson(eval);
            @SuppressWarnings("unchecked")
            Map<String, Object> evalMap = (Map<String, Object>) objectMapper.readValue(evalJson, Map.class);

            answer.setEvaluationScore(asDouble(evalMap.getOrDefault("overallScore", evalMap.getOrDefault("evaluationScore", evalMap.get("score"))), 60.0));
            answer.setTechnicalScore(asDouble(evalMap.getOrDefault("technicalScore", evalMap.get("technicalAccuracy")), 60.0));
            answer.setCommunicationScore(asDouble(evalMap.getOrDefault("communicationScore", evalMap.get("communication")), 60.0));
            answer.setConfidenceScore(asDouble(evalMap.getOrDefault("confidenceScore", 60.0), 60.0));
            
            answer.setFeedback(asString(evalMap.get("feedback"), "Voice evaluation completed."));
            answer.setImprovementSuggestions(asString(evalMap.get("improvementSuggestions"), null));
            answer.setStrengths(asJsonArrayString(evalMap.get("strengths")));
            answer.setWeaknesses(asJsonArrayString(evalMap.get("weaknesses")));

            // Calculate Fluency/Voice Quality Score
            double speed = answer.getSpeakingSpeed() != null ? answer.getSpeakingSpeed() : 130.0;
            int fillers = answer.getFillerWordsCount() != null ? answer.getFillerWordsCount() : 0;
            
            double voiceQuality = 100.0;
            if (speed < 100) voiceQuality -= (100 - speed) * 0.5;
            else if (speed > 160) voiceQuality -= (speed - 160) * 0.5;
            voiceQuality -= fillers * 2.0;
            
            answer.setFluencyScore(Math.max(0.0, Math.min(100.0, voiceQuality)));

        } catch (Exception e) {
            log.error("Voice evaluation failed", e);
            answer.setEvaluationScore(60.0);
            answer.setFeedback("Failed to evaluate voice answer completely. " + e.getMessage());
        }
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }

    private Double asDouble(Object o, Double def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.valueOf(o.toString()); } catch (Exception e) { return def; }
    }

    private String asString(Object o, String def) {
        if (o instanceof String s && !s.isBlank()) return s;
        return def;
    }

    private String asJsonArrayString(Object o) {
        if (o == null) return null;
        if (o instanceof java.util.List<?> list) {
            try { return objectMapper.writeValueAsString(list); } catch (Exception e) { return list.toString(); }
        }
        return o.toString();
    }
}
