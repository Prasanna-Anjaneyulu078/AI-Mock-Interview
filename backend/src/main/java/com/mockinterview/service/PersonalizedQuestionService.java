package com.mockinterview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.entity.Interview;
import com.mockinterview.entity.Question;
import com.mockinterview.entity.TestCase;
import com.mockinterview.repository.QuestionRepository;
import com.mockinterview.service.ai.AIProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PersonalizedQuestionService {

    private final AIProvider aiProvider;
    private final QuestionRepository questionRepository;
    private final ResumeService resumeService;
    private final ObjectMapper objectMapper;

    private static final double DEDUP_THRESHOLD = 0.7;
    private static final int MAX_ATTEMPTS = 2;

    private static final Map<String, String> LEVEL_DIFFICULTY = Map.of(
            "STARTER", "Easy",
            "STANDARD", "Medium",
            "ADVANCED", "Hard"
    );

    private static final Map<String, String> LEVEL_GUIDANCE = Map.of(
            "STARTER", "Focus ONLY on fundamentals: definitions, core concepts, basic coding constructs, and simple entry-level interview questions.",
            "STANDARD", "Focus on real-world development scenarios, debugging questions, applied concepts, and practical implementation questions drawn from the candidate's own projects.",
            "ADVANCED", "Focus on system design, software architecture, scalability, performance optimization, and deep trade-off discussions."
    );

    public PersonalizedQuestionService(AIProvider aiProvider, QuestionRepository questionRepository,
                                       ResumeService resumeService) {
        this.aiProvider = aiProvider;
        this.questionRepository = questionRepository;
        this.resumeService = resumeService;
        this.objectMapper = new ObjectMapper();
    }

    public void generateAndSaveAIQuestions(Interview interview, String role, String resumeText,
                                           String structuredProfile, int count, String level,
                                           Long userId, Long resumeId) {
        if (level == null) level = "STANDARD";
        String lvl = level.toUpperCase();
        String levelDifficulty = LEVEL_DIFFICULTY.getOrDefault(lvl, "Medium");
        String guidance = LEVEL_GUIDANCE.getOrDefault(lvl, LEVEL_GUIDANCE.get("STANDARD"));

        int hrCount, techCount, projCount, codeCount;
        switch (lvl) {
            case "STARTER":  hrCount = 2; techCount = 2; projCount = 2; codeCount = 1; break;
            case "ADVANCED": hrCount = 5; techCount = 8; projCount = 6; codeCount = 2; break;
            case "STANDARD":
            default:         hrCount = 3; techCount = 5; projCount = 5; codeCount = 2; break;
        }

        Set<String> seen = new HashSet<>();
        List<Question> past = new ArrayList<>();
        try {
            past.addAll(questionRepository.findByInterviewUserId(userId));
            if (resumeId != null) {
                past.addAll(questionRepository.findByInterviewResumeId(resumeId));
            }
        } catch (Exception ignored) {}
        
        for (Question q : past) {
            if (q.getQuestionText() != null) {
                seen.add(TextSimilarity.normalize(q.getQuestionText()));
            }
        }

        String resumeContext = resumeService.prepareResumeContext(resumeText, structuredProfile);
        String avoidList = "";
        // Phase 5: ensure at least codeCount coding questions are generated
        int minCode = codeCount;

        List<Question> accepted = new ArrayList<>();
        for (int attempt = 0; attempt < MAX_ATTEMPTS && accepted.size() < count; attempt++) {
            String aiResponse;
            try {
                aiResponse = aiProvider.generateQuestions(role, resumeContext, guidance, levelDifficulty,
                        hrCount, techCount, projCount, count, avoidList);
            } catch (Exception e) {
                System.err.println("⚠️ AI failed to generate questions: " + e.getMessage());
                break;
            }
            
            aiResponse = extractJson(aiResponse);
            List<Map<String, Object>> parsed = parseArray(aiResponse);
            if (parsed == null) continue;
            
            for (Map<String, Object> qMap : parsed) {
                String text = firstNonBlank(qMap, "question", "text");
                if (text == null) continue;
                if (TextSimilarity.isDuplicate(text, seen, DEDUP_THRESHOLD)) continue;
                
                Question q = toQuestion(interview, qMap, text, levelDifficulty);
                if (q == null) continue;
                
                accepted.add(q);
                seen.add(TextSimilarity.normalize(text));
                if (accepted.size() >= count) break;
            }
            avoidList = accepted.stream().map(Question::getQuestionText).collect(Collectors.joining("\n- "));
        }

        boolean savedFromAI = false;
        try {
            // Phase 5: guarantee minimum coding questions
            long codeQsInAccepted = accepted.stream()
                    .filter(q -> Boolean.TRUE.equals(q.getIsCodeQuestion())).count();
            if (codeQsInAccepted < minCode) {
                addFallbackCodingQuestions(interview, accepted, (int)(minCode - codeQsInAccepted), role, levelDifficulty, seen);
            }

            for (Question q : accepted) questionRepository.save(q);
            savedFromAI = !accepted.isEmpty();
        } catch (Exception e) {
            System.err.println("⚠️ Failed to save AI questions for interview " + interview.getId() + ": " + e.getMessage());
        }

        if (!savedFromAI) {
            saveFallbackQuestions(interview, count);
        }
    }

    public String generateIntroQuestion(String role, String structuredProfile) {
        try {
            String response = aiProvider.generateIntroQuestion(role, structuredProfile);
            if (response != null) {
                String cleaned = response.replaceAll("^[\"'`]+|[\"'`]+$", "").trim();
                if (!cleaned.isBlank() && cleaned.length() < 300) return cleaned;
            }
        } catch (Exception ignored) {}
        return fallbackIntro(role, structuredProfile);
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
            System.err.println("⚠️ Failed to parse AI question JSON: " + e.getMessage());
            return null;
        }
    }

    private Question toQuestion(Interview interview, Map<String, Object> qMap, String text, String levelDifficulty) {
        String category = firstNonBlank(qMap, "category", "type");
        String type = normalizeCategory(category);
        boolean isCode = qMap.get("isCodeQuestion") instanceof Boolean b && b;
        String idealAnswer = qMap.get("idealAnswer") instanceof String s ? s : "";
        String explanation = qMap.get("explanation") instanceof String s ? s : "";
        Question q = Question.builder()
                .interview(interview)
                .questionText(text.trim())
                .type(type)
                .isCodeQuestion(isCode)
                .expectedAnswer(idealAnswer)
                .explanation(explanation)
                .difficulty(levelDifficulty)
                .generatedByAI(true)
                .build();

        List<TestCase> testCases = parseTestCases(qMap);
        if (testCases != null) {
            for (TestCase tc : testCases) tc.setQuestion(q);
            q.setTestCases(testCases);
        }
        return q;
    }

    private List<TestCase> parseTestCases(Map<String, Object> qMap) {
        Object tcObj = qMap.get("testCases");
        if (!(tcObj instanceof List<?> list) || list.isEmpty()) return null;
        List<TestCase> testCases = new ArrayList<>();
        int idx = 1;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String input = m.get("input") instanceof String s ? s : "";
            String expected = m.get("expectedOutput") instanceof String s ? s : "";
            boolean hidden = m.get("hidden") instanceof Boolean b && b;
            testCases.add(TestCase.builder().input(input).expectedOutput(expected).hidden(hidden).name("case-" + (idx++)).build());
        }
        return testCases.isEmpty() ? null : testCases;
    }

    private String normalizeCategory(String category) {
        if (category == null) return "technical";
        String c = category.toLowerCase();
        if (c.contains("behav") || c.contains("hr")) return "behavioral";
        if (c.contains("proj") || c.contains("experien")) return "project";
        return "technical";
    }

    private String firstNonBlank(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

    private void saveFallbackQuestions(Interview interview, int count) {
        Map<String, String[]> fallbacks = new HashMap<>();
        fallbacks.put("How do you approach debugging a complex production issue?", new String[]{"problem-solving", "false"});
        fallbacks.put("Describe a challenging technical problem you solved recently and your solution.", new String[]{"behavioral", "false"});
        fallbacks.put("Explain a design decision you are proud of from a past project.", new String[]{"project", "false"});
        fallbacks.put("How do you ensure code quality and maintainability in your work?", new String[]{"technical", "false"});
        fallbacks.put("Walk me through how you would design a scalable system for a high-traffic application.", new String[]{"technical", "false"});
        fallbacks.put("How do you handle disagreements with teammates on technical decisions?", new String[]{"behavioral", "false"});
        fallbacks.put("Describe your approach to performance optimization in an application.", new String[]{"technical", "false"});
        fallbacks.put("How do you stay current with new technologies and industry trends?", new String[]{"behavioral", "false"});
        fallbacks.put("What is your process for writing and maintaining unit tests?", new String[]{"technical", "false"});
        fallbacks.put("Give an example of when you had to meet a tight deadline. How did you manage it?", new String[]{"situational", "false"});

        int i = 0;
        for (Map.Entry<String, String[]> fb : fallbacks.entrySet()) {
            if (i >= count) break;
            Question q = Question.builder()
                    .interview(interview)
                    .questionText(fb.getKey())
                    .type(fb.getValue()[0])
                    .isCodeQuestion(Boolean.parseBoolean(fb.getValue()[1]))
                    .generatedByAI(false)
                    .build();
            questionRepository.save(q);
            i++;
        }
    }

    /**
     * Phase 5: Adds N coding questions to the accepted list when the AI didn't generate
     * enough. Uses pre-defined challenges covering common algorithm patterns.
     */
    private void addFallbackCodingQuestions(Interview interview, List<Question> accepted,
                                             int needed, String role, String difficulty,
                                             Set<String> seen) {
        String[][] codingFallbacks = {
            {"Write a function to find the two numbers in an array that sum to a target value. Return their indices.",
             "coding", "python"},
            {"Implement a function that reverses a linked list iteratively.",
             "coding", "java"},
            {"Write a function to determine if a string is a palindrome, ignoring spaces and punctuation.",
             "coding", "javascript"},
            {"Given a binary tree, write a function to find its maximum depth.",
             "coding", "python"},
            {"Implement a function that finds all unique pairs in an array whose sum equals a given target.",
             "coding", "java"},
        };
        int added = 0;
        for (String[] fb : codingFallbacks) {
            if (added >= needed) break;
            String text = fb[0];
            if (TextSimilarity.isDuplicate(text, seen, DEDUP_THRESHOLD)) continue;
            Question q = Question.builder()
                    .interview(interview)
                    .questionText(text)
                    .type("coding")
                    .isCodeQuestion(true)
                    .codeType("write")
                    .codeLanguage(fb[2])
                    .difficulty(difficulty)
                    .generatedByAI(false)
                    .build();
            accepted.add(q);
            seen.add(TextSimilarity.normalize(text));
            added++;
        }
    }


    private String fallbackIntro(String role, String structuredProfile) {
        String r = role != null ? role.toLowerCase() : "";
        String project = jsonFirstArrayItem(structuredProfile, "projects");
        String skill = jsonFirstArrayItem(structuredProfile, "skills");
        if (r.contains("java")) return "Tell me about your experience building backend applications using Java and Spring Boot.";
        if (r.contains("frontend") || r.contains("react")) return "Walk me through the most challenging frontend project listed on your resume.";
        if (r.contains("ai") || r.contains("ml") || r.contains("data") || r.contains("machine")) return "Explain the AI or data project you are most proud of and the challenges you solved.";
        if (project != null) return "Walk me through " + project + " listed on your resume and the key challenges you solved.";
        if (skill != null) return "Tell me about your experience with " + skill + " as a " + (role != null ? role : "software") + " professional.";
        return "Tell me about your background and what excites you about the " + (role != null ? role : "software") + " role.";
    }

    private String jsonFirstArrayItem(String json, String key) {
        if (json == null) return null;
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            if (parsed instanceof Map) {
                Object arr = ((Map<?, ?>) parsed).get(key);
                if (arr instanceof List && !((List<?>) arr).isEmpty()) {
                    return String.valueOf(((List<?>) arr).get(0));
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
