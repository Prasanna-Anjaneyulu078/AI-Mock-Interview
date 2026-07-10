package com.mockinterview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.entity.Answer;
import com.mockinterview.entity.Interview;
import com.mockinterview.entity.Question;
import com.mockinterview.repository.QuestionRepository;
import com.mockinterview.service.ai.AIProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FollowUpService {

    private final AIProvider aiProvider;
    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_FOLLOWUPS_PER_QUESTION = 2;
    private static final int MAX_FOLLOWUPS_PER_INTERVIEW = 20;
    private static final double DEDUP_THRESHOLD = 0.7;

    public FollowUpService(AIProvider aiProvider, QuestionRepository questionRepository) {
        this.aiProvider = aiProvider;
        this.questionRepository = questionRepository;
        this.objectMapper = new ObjectMapper();
    }

    public List<Question> generateFollowUps(Interview interview, Question answered, Answer answer,
                                            String role, String adaptedLevelWord,
                                            String resumeContext, int alreadyGenerated) {
        if (alreadyGenerated >= MAX_FOLLOWUPS_PER_INTERVIEW) {
            return List.of();
        }
        String answerText = answer.getAnswerText();
        if (answerText == null || answerText.isBlank()) {
            return List.of();
        }

        String response;
        try {
            response = aiProvider.generateFollowUp(answered.getQuestionText(), answerText, role, adaptedLevelWord, resumeContext);
        } catch (Exception e) {
            System.err.println("⚠️ AI failed to generate follow-ups: " + e.getMessage());
            return List.of();
        }
        
        response = extractJson(response);
        List<Map<String, Object>> parsed = parseArray(response);
        if (parsed == null) {
            return List.of();
        }

        java.util.Set<String> seen = interview.getQuestions().stream()
                .map(Question::getQuestionText)
                .filter(Objects::nonNull)
                .map(TextSimilarity::normalize)
                .collect(Collectors.toSet());

        List<Question> result = new ArrayList<>();
        for (Map<String, Object> m : parsed) {
            if (result.size() + alreadyGenerated >= MAX_FOLLOWUPS_PER_INTERVIEW) break;
            if (result.size() >= MAX_FOLLOWUPS_PER_QUESTION) break;
            String text = firstNonBlank(m, "question", "text");
            if (text == null) continue;
            if (TextSimilarity.isDuplicate(text, seen, DEDUP_THRESHOLD)) continue;

            Question f = Question.builder()
                    .interview(interview)
                    .questionText(text.trim())
                    .type("followup")
                    .isCodeQuestion(false)
                    .isFollowUp(true)
                    .parentQuestionId(answered.getId())
                    .difficulty(adaptedLevelWord)
                    .generatedByAI(true)
                    .build();
            questionRepository.save(f);
            result.add(f);
            seen.add(TextSimilarity.normalize(text));
        }
        return result;
    }

    private String extractJson(String text) {
        if (text == null) return "[]";
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

    private List<Map<String, Object>> parseArray(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            Object parsed = objectMapper.readValue(response, Object.class);
            if (parsed instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = (List<Map<String, Object>>) parsed;
                return list;
            }
            return null;
        } catch (Exception e) {
            System.err.println("⚠️ Failed to parse follow-up JSON: " + e.getMessage());
            return null;
        }
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }
}
