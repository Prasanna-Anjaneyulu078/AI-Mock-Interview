package com.mockinterview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.entity.Answer;
import com.mockinterview.entity.Question;
import com.mockinterview.dto.AnswerRequest;
import com.mockinterview.service.ai.AIProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private static final double NEUTRAL_FALLBACK_SCORE = 60.0;

    private final AIProvider aiProvider;
    private final ObjectMapper objectMapper;

    public ScoringService(AIProvider aiProvider) {
        this.aiProvider = aiProvider;
        this.objectMapper = new ObjectMapper();
    }

    public void evaluateAnswer(Answer answer, Question question, AnswerRequest request) {
        // ─── Phase 3: Empty / skipped answer guard ───────────────────────────────
        String answerTextForCheck = request.getCode() != null && !request.getCode().isBlank()
                ? request.getCode() : request.getAnswerText();
        if (answerTextForCheck == null || answerTextForCheck.isBlank()
                || isUnansweredMarker(answerTextForCheck)) {
            log.info("Skipped answer detected — scoring as 0 with 'Question not answered'.");
            answer.setEvaluationScore(0.0);
            answer.setTechnicalScore(0.0);
            answer.setCommunicationScore(0.0);
            answer.setProblemSolvingScore(0.0);
            answer.setCodeQualityScore(0.0);
            answer.setFeedback("Question not answered.");
            answer.setImprovementSuggestions("Attempt to answer all questions for a complete evaluation.");
            return;
        }
        // ─────────────────────────────────────────────────────────────────────────
        try {
            String answerText = request.getCode() != null && !request.getCode().isBlank() 
                                ? request.getCode() 
                                : answer.getAnswerText();
            String requestJson = null;
            if (request.getCode() != null) {
                requestJson = "{\"language\": \"" + request.getLanguage() + "\"}";
            }
            
            String eval = aiProvider.evaluateAnswer(
                question.getQuestionText(), 
                answerText != null ? answerText : "No answer provided", 
                requestJson, 
                null // judge0Result handled via overload
            );
            
            if (eval == null) {
                throw new IllegalStateException("AI returned no evaluation for the answer.");
            }
            
            String evalJson = extractJson(eval);
            @SuppressWarnings("unchecked")
            Map<String, Object> evalMap = (Map<String, Object>) objectMapper.readValue(evalJson, Map.class);
            
            answer.setEvaluationScore(asDouble(evalMap.getOrDefault("evaluationScore", evalMap.get("score")), null));
            answer.setFeedback(asString(evalMap.get("feedback"), "The answer lacked sufficient technical detail to evaluate positively."));
            answer.setImprovementSuggestions(asString(evalMap.get("improvementSuggestions"), null));
            answer.setAnswerComparison(asString(evalMap.get("answerComparison"), null));
            answer.setTechnicalScore(asDouble(evalMap.getOrDefault("technicalScore", evalMap.get("technicalAccuracy")), null));
            answer.setCommunicationScore(asDouble(evalMap.getOrDefault("communicationScore", evalMap.get("communication")), null));
            answer.setProblemSolvingScore(asDouble(evalMap.getOrDefault("problemSolvingScore", evalMap.get("problemSolving")), null));
            answer.setCodeQualityScore(asDouble(evalMap.getOrDefault("codeQualityScore", evalMap.get("codeQuality")), null));
            answer.setProjectScore(asDouble(evalMap.get("projectScore"), null));
            answer.setConfidenceScore(asDouble(evalMap.get("confidenceScore"), null));
            answer.setStrengths(asJsonArrayString(evalMap.get("strengths")));
            answer.setWeaknesses(asJsonArrayString(evalMap.get("weaknesses")));
            answer.setRecommendations(asJsonArrayString(evalMap.get("recommendations")));
            
            // Advanced Voice Analytics
            if (request.getFillerWordsCount() != null) {
                answer.setFillerWordsCount(request.getFillerWordsCount());
            }
            if (request.getSpeakingSpeed() != null) {
                answer.setSpeakingSpeed(request.getSpeakingSpeed());
                
                // Calculate Fluency Score based on speed and filler words
                double speed = request.getSpeakingSpeed();
                int fillers = request.getFillerWordsCount() != null ? request.getFillerWordsCount() : 0;
                
                double fluency = 100.0;
                // Penalize for being too slow (< 100 wpm) or too fast (> 160 wpm)
                if (speed < 100) {
                    fluency -= (100 - speed) * 0.5;
                } else if (speed > 160) {
                    fluency -= (speed - 160) * 0.5;
                }
                
                // Penalize for filler words
                fluency -= fillers * 2.0;
                
                // Average with communication score if available
                if (answer.getCommunicationScore() != null) {
                    fluency = (fluency + answer.getCommunicationScore()) / 2.0;
                }
                
                answer.setFluencyScore(Math.max(0.0, Math.min(100.0, fluency)));
            }

        } catch (Exception e) {
            log.error("AI scoring failed for interview {}", 
                (answer.getQuestion() != null && answer.getQuestion().getInterview() != null) 
                    ? answer.getQuestion().getInterview().getId() : null, e);
            
            answer.setEvaluationScore(NEUTRAL_FALLBACK_SCORE);
            answer.setTechnicalScore(NEUTRAL_FALLBACK_SCORE);
            answer.setCommunicationScore(NEUTRAL_FALLBACK_SCORE);
            answer.setProblemSolvingScore(NEUTRAL_FALLBACK_SCORE);
            answer.setCodeQualityScore(NEUTRAL_FALLBACK_SCORE);
            answer.setProjectScore(NEUTRAL_FALLBACK_SCORE);
            answer.setConfidenceScore(NEUTRAL_FALLBACK_SCORE);
            answer.setFeedback("Failed to evaluate answer. " + e.getMessage());
            if (answer.getFeedback() == null) {
                answer.setFeedback("The answer could not be automatically evaluated this time. Please try to elaborate with concrete detail and examples.");
            }
        }
    }

    public void evaluateAnswer(Answer answer, Question question, AnswerRequest request, Judge0Result judge0) {
        if (judge0 != null) {
            try {
                String answerText = request.getCode();
                String requestJson = "{\"language\": \"" + request.getLanguage() + "\"}";
                
                String judge0Str = judge0.isPassed() ? "Passed all tests" : "Failed some tests";
                
                String eval = aiProvider.evaluateAnswer(
                    question.getQuestionText(), 
                    answerText, 
                    requestJson, 
                    judge0Str
                );
                
                String evalJson = extractJson(eval);
                @SuppressWarnings("unchecked")
                Map<String, Object> evalMap = (Map<String, Object>) objectMapper.readValue(evalJson, Map.class);
                
                answer.setEvaluationScore(asDouble(evalMap.getOrDefault("evaluationScore", evalMap.get("score")), null));
                answer.setFeedback(asString(evalMap.get("feedback"), "Code execution completed."));
                // (populate other fields similar to above...)
                answer.setTechnicalScore(asDouble(evalMap.getOrDefault("technicalScore", evalMap.get("technicalAccuracy")), null));
                answer.setCodeQualityScore(asDouble(evalMap.getOrDefault("codeQualityScore", evalMap.get("codeQuality")), null));
            } catch (Exception e) {
                 log.error("AI code evaluation failed", e);
                 evaluateAnswer(answer, question, request); // fallback to without judge0
            }
        } else {
            evaluateAnswer(answer, question, request);
        }

        if (judge0 == null || judge0.getTotalTests() <= 0) {
            return;
        }

        // ─── Phase 6: Weighted Coding Evaluation Formula ─────────────────────────
        // Test Cases = 40%, Code Quality = 25%, Complexity = 15%,
        // Optimization = 10%, Communication = 10%
        double passRate  = judge0.getPassedTests() * 100.0 / judge0.getTotalTests();
        double aiScore   = answer.getEvaluationScore() != null ? answer.getEvaluationScore() : NEUTRAL_FALLBACK_SCORE;
        double codeQ     = answer.getCodeQualityScore()   != null ? answer.getCodeQualityScore()   : aiScore;
        double commScore = answer.getCommunicationScore() != null ? answer.getCommunicationScore() : aiScore;

        // Complexity & Optimization are inferred from the AI holistic score as a proxy
        double complexity    = aiScore;
        double optimization  = aiScore;

        double weighted = Math.round(
                0.40 * passRate
              + 0.25 * codeQ
              + 0.15 * complexity
              + 0.10 * optimization
              + 0.10 * commScore
        );
        answer.setEvaluationScore(Math.min(100.0, Math.max(0.0, weighted)));
        // ─────────────────────────────────────────────────────────────────────────

        String suffix = judge0.isPassed()
                ? " Code passed all " + judge0.getTotalTests() + " test cases via Judge0."
                : " Code passed " + judge0.getPassedTests() + "/" + judge0.getTotalTests()
                  + " test cases via Judge0.";
        String existing = answer.getFeedback() != null ? answer.getFeedback() : "";
        answer.setFeedback(existing + suffix);
    }

    public String generateFinalFeedback(String interviewType, String qaContext) {
        return aiProvider.generateFeedback(interviewType, qaContext);
    }

    /**
     * Phase 3: Detects common "no answer" markers that arrive when the user
     * skips a question or speech recognition returns garbage.
     */
    private boolean isUnansweredMarker(String text) {
        if (text == null) return true;
        String t = text.strip().toLowerCase();
        return t.isEmpty()
            || t.equals("[no speech detected]")
            || t.equals("error transcribing audio.")
            || t.equals("mock transcribed text since assemblyai key is missing.")
            || t.startsWith("[no speech")
            || t.startsWith("[transcription failed");
    }

    private Double asDouble(Object o, Double def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.valueOf(o.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private String asString(Object o, String def) {
        if (o instanceof String s && !s.isBlank()) return s;
        return def;
    }

    private String asJsonArrayString(Object o) {
        if (o == null) return null;
        if (o instanceof List<?> list) {
            try {
                return objectMapper.writeValueAsString(list);
            } catch (Exception e) {
                return list.toString();
            }
        }
        return o.toString();
    }
    
    private String extractJson(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim();
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
}

