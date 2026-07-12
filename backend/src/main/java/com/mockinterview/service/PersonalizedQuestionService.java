package com.mockinterview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.entity.Interview;
import com.mockinterview.entity.Question;
import com.mockinterview.entity.TestCase;
import com.mockinterview.repository.InterviewRepository;
import com.mockinterview.repository.QuestionRepository;
import com.mockinterview.service.ai.AIProvider;
import com.mockinterview.util.InterviewModes;
import com.mockinterview.exception.AIProviderException;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PersonalizedQuestionService {

    private final AIProvider aiProvider;
    private final QuestionRepository questionRepository;
    private final ResumeService resumeService;
    private final InterviewRepository interviewRepository;
    private final ObjectMapper objectMapper;
    private final LocalCodingQuestionProvider localCodingQuestionProvider;
    private final com.mockinterview.repository.QuestionBankRepository questionBankRepository;

    private static final double DEDUP_THRESHOLD = 0.7;
    private static final int MAX_ATTEMPTS = 2;

    private static final Map<String, String> LEVEL_DIFFICULTY = Map.of(
            "STARTER", "Easy",
            "BEGINNER", "Easy",
            "STANDARD", "Medium",
            "INTERMEDIATE", "Medium",
            "ADVANCED", "Hard"
    );

    private static final Map<String, String> LEVEL_GUIDANCE = Map.of(
            "STARTER", "Focus ONLY on fundamentals: definitions, core concepts, basic coding constructs, and simple entry-level interview questions.",
            "BEGINNER", "Focus ONLY on fundamentals: definitions, core concepts, basic coding constructs, and simple entry-level interview questions.",
            "STANDARD", "Focus on real-world development scenarios, debugging questions, applied concepts, and practical implementation questions drawn from the candidate's own projects.",
            "INTERMEDIATE", "Focus on real-world development scenarios, debugging questions, applied concepts, and practical implementation questions drawn from the candidate's own projects.",
            "ADVANCED", "Focus on system design, software architecture, scalability, performance optimization, and deep trade-off discussions."
    );

    public PersonalizedQuestionService(AIProvider aiProvider, QuestionRepository questionRepository,
                                       ResumeService resumeService, InterviewRepository interviewRepository,
                                       LocalCodingQuestionProvider localCodingQuestionProvider,
                                       com.mockinterview.repository.QuestionBankRepository questionBankRepository) {
        this.aiProvider = aiProvider;
        this.questionRepository = questionRepository;
        this.resumeService = resumeService;
        this.interviewRepository = interviewRepository;
        this.localCodingQuestionProvider = localCodingQuestionProvider;
        this.questionBankRepository = questionBankRepository;
        this.objectMapper = new ObjectMapper();
    }

    public void generateAndSaveAIQuestions(Interview interview, String role, String resumeText,
                                           String structuredProfile, int hrCount, int techCount, 
                                           int projCount, int codeCount, int interestCount, String level,
                                           Long userId, Long resumeId, String interviewMode) {
        if (level == null) level = "STANDARD";
        String lvl = level.toUpperCase();
        String levelDifficulty = LEVEL_DIFFICULTY.getOrDefault(lvl, "Medium");
        String guidance = LEVEL_GUIDANCE.getOrDefault(lvl, LEVEL_GUIDANCE.get("STANDARD"));
        
        if (interview.getInterviewMode() != null && interview.getInterviewMode().equals("INTEREST_BASED") 
            && interview.getSelectedInterests() != null) {
            guidance += "\nCRITICAL CONSTRAINT: ONLY ask questions directly related to the following interests: " + interview.getSelectedInterests();
        }
        
        int count = hrCount + techCount + projCount + codeCount + interestCount;
        if (count == 0) return;

        boolean codingMode = InterviewModes.CODING.equals(InterviewModes.normalize(interviewMode));
        log.info("[INTERVIEW_MODE] {}", InterviewModes.normalize(interviewMode));

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
        AIProviderException capturedException = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS && accepted.size() < count; attempt++) {
            String aiResponse;
            try {
                aiResponse = aiProvider.generateQuestions(interviewMode, role, resumeContext, guidance, levelDifficulty,
                        hrCount, techCount, projCount, codeCount, interestCount, interview.getSelectedInterests(), count, avoidList);
            } catch (AIProviderException e) {
                System.err.println("⚠️ AI Provider Exception: " + e.getMessage());
                capturedException = e;
                break;
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
                
                Question q = toQuestion(interview, qMap, text, levelDifficulty, interviewMode);
                if (q == null) continue;

                if (codingMode) {
                    log.info("[QUESTION_GENERATED] CODING title='{}'", q.getTitle());
                }
                accepted.add(q);
                seen.add(TextSimilarity.normalize(text));
                if (accepted.size() >= count) break;
            }
            avoidList = accepted.stream().map(Question::getQuestionText).collect(Collectors.joining("\n- "));
        }

        try {
            // Phase 5: guarantee minimum coding questions
            long codeQsInAccepted = accepted.stream()
                    .filter(q -> Boolean.TRUE.equals(q.getIsCodeQuestion())).count();
            if (codeQsInAccepted < minCode) {
                String genLang = interview.getCodingLanguage() != null ? interview.getCodingLanguage() : "java";
                addFallbackCodingQuestions(interview, accepted, (int)(minCode - codeQsInAccepted), role, levelDifficulty, seen, genLang);
            }

            for (Question q : accepted) {
                // ── CODING-mode integrity guard ──
                // A CODING interview MUST contain ONLY coding questions. Defensively reject
                // any non-coding question before it is persisted, so a bad AI payload or a
                // future regression can never pollute a coding interview with MCQ /
                // behavioral / HR / resume questions.
                if (codingMode && !Boolean.TRUE.equals(q.getIsCodeQuestion())) {
                    log.error("[INVALID_QUESTION_TYPE] Expected CODING Received {} title='{}'",
                            q.getType(), q.getTitle());
                    throw new IllegalStateException(
                            "Invalid question type for coding interview: expected CODING but received "
                                    + (q.getType() == null ? "null" : q.getType()));
                }
                questionRepository.save(q);
                if (codingMode) {
                    log.info("[QUESTION_SAVED] CODING title='{}'", q.getTitle());
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to save AI questions for interview " + interview.getId() + ": " + e.getMessage());
        }

        if (accepted.size() < count) {
            forceSaveFallbackQuestions(interview, count - accepted.size(), seen, interviewMode);
        }

        if (capturedException != null) {
            System.err.printf("[AI_PROVIDER_ERROR] Provider=%s Status=%d Reason=%s Fallback=true InterviewMode=%s%n",
                    capturedException.getProviderName(),
                    capturedException.getHttpStatus(),
                    capturedException.getMessage(),
                    interviewMode != null ? interviewMode : "RESUME");

            interview.setFallbackActivated(true);
            interview.setAiProviderUsed(capturedException.getProviderName());
            interview.setProviderError(capturedException.getMessage());
            interview.setInterviewSource("Local Question Engine");
            interviewRepository.save(interview);

            capturedException.setFallbackUsed(true);
            capturedException.setSelectedInterviewMode(interviewMode != null ? interviewMode : "RESUME");
            capturedException.setQuestionSource("Local Question Engine");
            throw capturedException;
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

    private Question toQuestion(Interview interview, Map<String, Object> qMap, String text, String levelDifficulty, String interviewMode) {
        String category = firstNonBlank(qMap, "category", "type");
        String type = normalizeCategory(category);
        boolean isCode = qMap.get("isCodeQuestion") instanceof Boolean b && b;

        // ── STRICT CODING MODE ENFORCEMENT ──
        // A CODING interview must contain ONLY coding questions. Force the type/flag
        // regardless of what the AI returned, and log if the AI tried to sneak a
        // non-coding question into a coding interview.
        boolean codingMode = InterviewModes.CODING.equals(InterviewModes.normalize(interviewMode));
        if (codingMode) {
            if (!isCode || !"coding".equals(type)) {
                log.warn("[INVALID_QUESTION_TYPE] Expected CODING Received {} — coercing to coding",
                        isCode ? type : (category != null ? category : "UNKNOWN"));
            }
            isCode = true;
            type = "coding";
        }
        String idealAnswer = qMap.get("idealAnswer") instanceof String s ? s : "";
        String explanation = qMap.get("explanation") instanceof String s ? s : "";

        // Parse coding problem detail fields (Issue #2)
        String title = qMap.get("title") instanceof String s ? s : null;
        String problemDescription = qMap.get("problemDescription") instanceof String s ? s : null;
        String exampleInput = qMap.get("exampleInput") instanceof String s ? s : null;
        String exampleOutput = qMap.get("exampleOutput") instanceof String s ? s : null;
        String constraints = qMap.get("constraints") instanceof String s ? s : null;
        String starterCode = firstNonBlank(qMap, "starterCode", "codeSnippet");
        String solutionCode = qMap.get("solutionCode") instanceof String s ? s : null;
        String codeLanguage = firstNonBlank(qMap, "codeLanguage", "language");
        if (codeLanguage == null && isCode && interview.getCodingLanguage() != null) {
            codeLanguage = interview.getCodingLanguage();
        }

        // Sanitize: strip any implementation body, keep only the method signature stub
        if (starterCode != null) {
            String langForSanitize = codeLanguage != null ? codeLanguage : "javascript";
            starterCode = com.mockinterview.util.StarterCodeSanitizer.sanitize(starterCode, langForSanitize);
        }
        String tags = qMap.get("tags") instanceof String s ? s : null;
        String timeComplexity = qMap.get("timeComplexity") instanceof String s ? s : null;
        String codeType = qMap.get("codeType") instanceof String s ? s : (isCode ? "write" : null);

        Question q = Question.builder()
                .interview(interview)
                .questionText(text.trim())
                .type(type)
                .isCodeQuestion(isCode)
                .expectedAnswer(idealAnswer)
                .explanation(explanation)
                .difficulty(levelDifficulty)
                .generatedByAI(true)
                .title(title)
                .problemDescription(problemDescription)
                .exampleInput(exampleInput)
                .exampleOutput(exampleOutput)
                .constraints(constraints)
                .starterCode(starterCode)
                .solutionCode(solutionCode)
                .tags(tags)
                .timeComplexity(timeComplexity)
                .codeType(codeType)
                .codeLanguage(codeLanguage)
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
        if (c.contains("cod")) return "coding";
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

    public void forceSaveFallbackQuestions(Interview interview, int needed, Set<String> seen, String mode) {
        if (seen == null) seen = new HashSet<>();
        if (mode == null) mode = "RESUME";

        String role = interview.getInterviewType() != null ? interview.getInterviewType() : "CS_FUNDAMENTALS";
        String difficulty = LEVEL_DIFFICULTY.getOrDefault(
                interview.getDifficulty() != null ? interview.getDifficulty().toUpperCase() : "STANDARD", "MEDIUM").toUpperCase();

        if (mode.equalsIgnoreCase("CODING") || mode.equalsIgnoreCase("CODING_INTERVIEW")) {
            log.info("[FALLBACK_CODING_PROVIDER_USED] true mode=CODING");
            int codingAdded = 0;
            while (codingAdded < needed) {
                Question cq = localCodingQuestionProvider.buildQuestion(
                        interview, difficulty, interview.getCodingLanguage(), seen);
                if (cq == null) break; // pool exhausted
                questionRepository.save(cq);
                log.info("[QUESTION_SAVED] CODING title='{}'", cq.getTitle());
                codingAdded++;
            }
            return;
        }

        // For non-coding modes, query DB dynamically based on mode mapping
        String category = "technical";
        if (mode.equalsIgnoreCase("BEHAVIORAL") || mode.equalsIgnoreCase("HR")) {
            category = "behavioral";
        } else if (mode.equalsIgnoreCase("RESUME_BASED") || mode.equalsIgnoreCase("RESUME") || mode.equalsIgnoreCase("PROJECT")) {
            category = "project";
        }
        
        List<com.mockinterview.entity.QuestionBank> dbQuestions = questionBankRepository.findRandomQuestions(role, difficulty, category, needed);

        // Map DB Questions to Interview Questions
        List<Question> saved = new ArrayList<>();
        for (com.mockinterview.entity.QuestionBank qb : dbQuestions) {
            if (seen.contains(qb.getQuestionText())) continue;
            
            Question q = new Question();
            q.setInterview(interview);
            q.setQuestionText(qb.getQuestionText());
            q.setType(qb.getCategory());
            q.setDifficulty(difficulty);
            q.setIsCodeQuestion(false);
            q.setExpectedAnswer(qb.getExpectedAnswer());
            q.setExplanation("Fallback explanation for: " + qb.getQuestionText());
            q.setGeneratedByAI(false);
            
            questionRepository.save(q);
            saved.add(q);
            seen.add(qb.getQuestionText());
            log.info("[QUESTION_SAVED] FALLBACK category={} difficulty={}", q.getType(), q.getDifficulty());
            if (saved.size() >= needed) break;
        }
        
        // If still lacking, try hybrid pulling anything available for this role
        if (saved.size() < needed) {
            int remaining = needed - saved.size();
            List<com.mockinterview.entity.QuestionBank> fallbackHybrid = questionBankRepository.findRandomQuestions(role, difficulty, "technical", remaining);
            for (com.mockinterview.entity.QuestionBank qb : fallbackHybrid) {
                if (seen.contains(qb.getQuestionText())) continue;
                
                Question q = new Question();
                q.setInterview(interview);
                q.setQuestionText(qb.getQuestionText());
                q.setType(qb.getCategory());
                q.setDifficulty(difficulty);
                q.setIsCodeQuestion(false);
                q.setExpectedAnswer(qb.getExpectedAnswer());
                q.setExplanation("Fallback explanation for: " + qb.getQuestionText());
                q.setGeneratedByAI(false);
                
                questionRepository.save(q);
                saved.add(q);
                seen.add(qb.getQuestionText());
                if (saved.size() >= needed) break;
            }
        }
    }

    /**
     * Phase 5: Adds N coding questions to the accepted list when the AI didn't generate
     * enough. Delegates to {@link LocalCodingQuestionProvider} so CODING mode never
     * degrades into MCQ / behavioral questions.
     */

    private void addFallbackCodingQuestions(Interview interview, List<Question> accepted,
                                             int needed, String role, String difficulty,
                                             Set<String> seen, String defaultLanguage) {
        String lang = defaultLanguage != null ? defaultLanguage : "javascript";
        int added = 0;
        while (added < needed) {
            Question cq = localCodingQuestionProvider.buildQuestion(interview, difficulty, lang, seen);
            if (cq == null) break; // pool exhausted
            accepted.add(cq);
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
