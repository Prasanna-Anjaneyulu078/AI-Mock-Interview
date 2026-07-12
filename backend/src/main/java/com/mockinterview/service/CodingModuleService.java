package com.mockinterview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.service.ai.AIProvider;
import com.mockinterview.entity.*;
import com.mockinterview.repository.*;
import com.mockinterview.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mockinterview.exception.AIProviderException;
import com.mockinterview.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(noRollbackFor = AIProviderException.class)
public class CodingModuleService {

    private final CodingQuestionRepository codingQuestionRepository;
    private final CodingSubmissionRepository codingSubmissionRepository;
    private final CodingResultRepository codingResultRepository;
    private final Judge0Service judge0Service;
    private final AIProvider aiProvider;
    private final ObjectMapper objectMapper;
    private final InterviewRepository interviewRepository;

    public CodingModuleService(CodingQuestionRepository codingQuestionRepository,
                               CodingSubmissionRepository codingSubmissionRepository,
                               CodingResultRepository codingResultRepository,
                               Judge0Service judge0Service,
                               AIProvider aiProvider,
                               ObjectMapper objectMapper,
                               InterviewRepository interviewRepository) {
        this.codingQuestionRepository = codingQuestionRepository;
        this.codingSubmissionRepository = codingSubmissionRepository;
        this.codingResultRepository = codingResultRepository;
        this.judge0Service = judge0Service;
        this.aiProvider = aiProvider;
        this.objectMapper = objectMapper;
        this.interviewRepository = interviewRepository;
    }

    /**
     * Generates a coding question using AI and saves it in the database.
     */
    public CodingQuestion generateCodingQuestion(Long interviewId, Long userId) {
        if (interviewId == null) throw new IllegalArgumentException("Interview ID is required");
        log.info("Generating coding question for interview: {}", interviewId);
        
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));
        
        if (!interview.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to access this interview");
        }

        // 1. Return existing question if already generated
        List<CodingQuestion> existing = codingQuestionRepository.findByInterviewId(interviewId);
        if (existing != null && !existing.isEmpty()) {
            CodingQuestion q = existing.get(0);
            if (q.getTestCases() != null) {
                q.getTestCases().size(); // Force initialization
            }
            return q;
        }

        String prompt = String.format(
            "Generate exactly one complex coding question for a candidate interviewing for a %s role.\n" +
            "Difficulty: %s\n" +
            "The candidate has these resume skills: %s\n" +
            "The candidate has these interests: %s\n\n" +
            "Return the question strictly in JSON format as follows:\n" +
            "{\n" +
            "  \"title\": \"Question Title\",\n" +
            "  \"description\": \"Detailed problem statement...\",\n" +
            "  \"constraints\": \"Any constraints on input/time/memory\",\n" +
            "  \"languageSupport\": \"java,python,javascript,c++,c\",\n" +
            "  \"starterCode\": {\n" +
            "    \"java\": \"class Solution {\\n    public int solve(int input) {\\n        // Write your code here\\n    }\\n}\",\n" +
            "    \"python\": \"def solve(input):\\n    pass\",\n" +
            "    \"javascript\": \"function solve(input) {\\n    // Write your code here\\n}\",\n" +
            "    \"c++\": \"class Solution {\\npublic:\\n    int solve(int input) {\\n        // Write your code here\\n    }\\n};\"\n" +
            "  },\n" +
            "  \"solutionCode\": {\n" +
            "    \"java\": \"<full correct Java implementation>\",\n" +
            "    \"python\": \"<full correct Python implementation>\",\n" +
            "    \"javascript\": \"<full correct JavaScript implementation>\"\n" +
            "  },\n" +
            "  \"timeLimit\": 2,\n" +
            "  \"memoryLimit\": 128000,\n" +
            "  \"testCases\": [\n" +
            "    { \"name\": \"Example 1\", \"input\": \"1 2\", \"expectedOutput\": \"3\", \"isHidden\": false },\n" +
            "    { \"name\": \"Hidden Test 1\", \"input\": \"10 20\", \"expectedOutput\": \"30\", \"isHidden\": true }\n" +
            "  ]\n" +
            "}\n" +
            "IMPORTANT: starterCode must contain ONLY empty method signatures with stub comments. solutionCode contains the full implementation.",
            interview.getInterviewType(), interview.getDifficulty(), interview.getResumeText(), interview.getSelectedInterests()
        );

        Map<String, Object> data = null;
        boolean isFallback = false;
        try {
            String rawJson = aiProvider.generate(prompt);
            if (rawJson == null) throw new RuntimeException("AI Provider returned null");
            rawJson = extractJson(rawJson);
            data = objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            isFallback = true;
            System.err.println("⚠️ AI Coding Question Generation Failed. Using fallback question.");
            // 2. Fallback to a hardcoded question if AI fails
            data = Map.of(
                "title", "Two Sum",
                "description", "Given an array of integers and an integer target, return indices of the two numbers such that they add up to target.\nYou may assume that each input would have exactly one solution, and you may not use the same element twice.",
                "constraints", "2 <= nums.length <= 10^4\n-10^9 <= nums[i] <= 10^9\n-10^9 <= target <= 10^9",
                "languageSupport", "java,python,javascript",
                "starterCode", Map.of(
                    "java", "class Solution {\n    public int[] twoSum(int[] nums, int target) {\n        \n    }\n}",
                    "python", "class Solution:\n    def twoSum(self, nums, target):\n        pass",
                    "javascript", "function twoSum(nums, target) {\n\n}"
                ),
                "timeLimit", 2,
                "memoryLimit", 128000,
                "testCases", List.of(
                    Map.of("name", "Example 1", "input", "[2,7,11,15]\n9", "expectedOutput", "[0,1]", "isHidden", false),
                    Map.of("name", "Example 2", "input", "[3,2,4]\n6", "expectedOutput", "[1,2]", "isHidden", false),
                    Map.of("name", "Hidden Test 1", "input", "[3,3]\n6", "expectedOutput", "[0,1]", "isHidden", true)
                )
            );
        }
        
        try {
            data = validateAndCleanCodingData(data, interview.getDifficulty());

            // Sanitize starter code — strip any implementation bodies, keep only signatures
            @SuppressWarnings("unchecked")
            Map<String, Object> rawStarterMap = data.get("starterCode") instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : new java.util.LinkedHashMap<>();
            Map<String, Object> sanitizedStarterMap = com.mockinterview.util.StarterCodeSanitizer.sanitizeMap(rawStarterMap);

            // Extract solutionCode (stored server-side only — never exposed to frontend)
            String solutionCodeJson = null;
            if (data.get("solutionCode") != null) {
                try { solutionCodeJson = objectMapper.writeValueAsString(data.get("solutionCode")); } catch (Exception ignored) {}
            }

            // ── Pre-save field extraction with guaranteed non-null / non-blank values ──
            String title       = requireNonBlank(data.get("title"),       "Coding Challenge");
            String description = requireNonBlank(data.get("description"), "Solve the following coding problem efficiently.");
            String constraints = requireNonBlank(data.get("constraints"), "No specific constraints.");
            String langSupport = requireNonBlank(data.get("languageSupport"), "java,python,javascript,c++");
            String difficulty  = requireNonBlank(interview.getDifficulty(), "Medium");

            log.info("[CODING_QUESTION_SAVE] title='{}' descLen={} difficulty='{}' langs='{}'",
                    title, description.length(), difficulty, langSupport);

            CodingQuestion q = CodingQuestion.builder()
                    .interview(interview)
                    .title(title)
                    .description(description)
                    .constraints(constraints)
                    .difficulty(difficulty)
                    .languageSupport(langSupport)
                    .starterCode(objectMapper.writeValueAsString(sanitizedStarterMap))
                    .solutionCode(solutionCodeJson)
                    .timeLimit(parseInteger(data.get("timeLimit"), 2))
                    .memoryLimit(parseInteger(data.get("memoryLimit"), 128000))
                    .build();

            q = codingQuestionRepository.save(q);
            log.info("[CODING_QUESTION_SAVED] id={}", q.getId());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tcData = (List<Map<String, Object>>) data.get("testCases");
            if (tcData != null && !tcData.isEmpty()) {
                for (Map<String, Object> tc : tcData) {
                    String tcName = requireNonBlank(tc.get("name"), "Test Case");
                    String tcInput = asString(tc.get("input"));
                    String tcOutput = asString(tc.get("expectedOutput"));
                    boolean tcHidden = parseBoolean(tc.get("isHidden"), false);
                    CodingTestCase codingTestCase = CodingTestCase.builder()
                            .codingQuestion(q)
                            .name(tcName)
                            .input(tcInput)
                            .expectedOutput(tcOutput)
                            .isHidden(tcHidden)
                            .build();
                    q.getTestCases().add(codingTestCase);
                    log.info("[TEST_CASE_ADDED] name='{}' hidden={}", tcName, tcHidden);
                }
                q = codingQuestionRepository.save(q);
            } else {
                // Fallback: generate at least one visible test case so Run button can work
                log.warn("[TEST_CASE_MISSING] AI returned no test cases for question id={}. Adding placeholder.", q.getId());
                CodingTestCase placeholder = CodingTestCase.builder()
                        .codingQuestion(q)
                        .name("Example 1")
                        .input("(Provide your own test input)")
                        .expectedOutput("(Expected output here)")
                        .isHidden(false)
                        .build();
                q.getTestCases().add(placeholder);
                q = codingQuestionRepository.save(q);
            }
            if (q.getTestCases() != null) {
                q.getTestCases().size(); // Force initialization
            }
            
            if (isFallback) {
                AIProviderException ex = new AIProviderException("AI Services", 429, "AI_PROVIDER_LIMIT", "AI provider quota exceeded. Generated fallback questions.", null);
                ex.setFallbackUsed(true);
                ex.setFallbackData(com.mockinterview.dto.CodingQuestionDTO.fromEntity(q));
                throw ex;
            }
            
            return q;
        } catch (AIProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save coding question: " + e.getMessage(), e);
        }
    }

    /**
     * Executes code against visible test cases for "Run Code" functionality without saving.
     */
    public Judge0Result runSampleCode(Long questionId, String sourceCode, String language, Long userId) {
        log.info("Running sample code for question: {}", questionId);
        CodingQuestion question = codingQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Coding Question not found"));
                
        if (!question.getInterview().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to access this question");
        }

        List<CodingTestCase> cases = question.getTestCases();
        if (cases == null) {
            cases = Collections.emptyList();
        }

        List<TestCase> standardTestCases = cases.stream()
                .filter(ctc -> ctc.getIsHidden() == null || !ctc.getIsHidden())
                .map(ctc -> TestCase.builder()
                    .input(ctc.getInput())
                    .expectedOutput(ctc.getExpectedOutput())
                    .name(ctc.getName())
                    .hidden(false)
                    .build()
                ).collect(Collectors.toList());

        return judge0Service.execute(sourceCode, language, standardTestCases);
    }

    /**
     * Submits code to Judge0, executing it against all CodingTestCases,
     * and saves the metrics in CodingSubmission and CodingResult.
     */
    public CodingSubmission submitCode(Long questionId, String sourceCode, String language, Long userId) {
        log.info("Submitting code for question: {}", questionId);
        CodingQuestion question = codingQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Coding Question not found"));

        if (!question.getInterview().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to access this question");
        }

        List<CodingTestCase> cases = question.getTestCases();
        if (cases == null) {
            cases = Collections.emptyList();
        }

        // Convert CodingTestCase to standard TestCase for Judge0Service compatibility
        List<TestCase> standardTestCases = cases.stream().map(ctc -> 
            TestCase.builder()
                .input(ctc.getInput())
                .expectedOutput(ctc.getExpectedOutput())
                .name(ctc.getName())
                .hidden(ctc.getIsHidden() != null ? ctc.getIsHidden() : false)
                .build()
        ).collect(Collectors.toList());

        Judge0Result result = judge0Service.execute(sourceCode, language, standardTestCases);

        CodingSubmission submission = CodingSubmission.builder()
                .codingQuestion(question)
                .interview(question.getInterview())
                .sourceCode(sourceCode)
                .language(language)
                .executionTime(result != null ? result.getExecutionTime() : null)
                .memoryUsage(result != null ? result.getMemoryUsage() : null)
                .status(result != null ? result.getStatusDescription() : "Failed")
                .build();
        submission = codingSubmissionRepository.save(submission);

        CodingResult codingResult = CodingResult.builder()
                .submission(submission)
                .passedTests(result != null ? result.getPassedTests() : 0)
                .failedTests(result != null ? (result.getTotalTests() - result.getPassedTests()) : 0)
                .totalTests(result != null ? result.getTotalTests() : 0)
                .compileOutput(result != null ? result.getCompileOutput() : null)
                .stdout(result != null ? result.getStdout() : null)
                .stderr(result != null ? result.getStderr() : null)
                .build();
        codingResultRepository.save(codingResult);
        log.info("Submission processed successfully for question: {}", questionId);

        return submission;
    }

    /**
     * Evaluates a submission using the 40/20/15/15/10 weighted formula.
     */
    public CodingResult evaluateSubmission(Long submissionId, Long userId) {
        log.info("Evaluating submission: {}", submissionId);
        CodingSubmission submission = codingSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));

        if (!submission.getInterview().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Not authorized to access this submission");
        }

        CodingResult codingResult = codingResultRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Coding Result not found"));

        String prompt = String.format(
            "Evaluate this code submission for the following question.\n" +
            "Title: %s\nDescription: %s\n" +
            "Source Code (Language: %s):\n%s\n\n" +
            "Provide scores out of 100 for codeQuality, timeComplexity, spaceComplexity, and style.\n" +
            "Provide a list of strengths, weaknesses, and optimizationSuggestions.\n" +
            "Return the strictly valid JSON format as follows:\n" +
            "{\n" +
            "  \"codeQuality\": 85.0,\n" +
            "  \"timeComplexity\": 80.0,\n" +
            "  \"spaceComplexity\": 90.0,\n" +
            "  \"style\": 75.0,\n" +
            "  \"strengths\": \"Uses efficient datastructure...\",\n" +
            "  \"weaknesses\": \"Lacks comments...\",\n" +
            "  \"optimizationSuggestions\": \"Could use a HashMap instead of nested loops...\"\n" +
            "}",
            submission.getCodingQuestion().getTitle(),
            submission.getCodingQuestion().getDescription(),
            submission.getLanguage(),
            submission.getSourceCode()
        );

        String rawJson = null;
        try {
            rawJson = aiProvider.generate(prompt);
            if (rawJson == null) throw new RuntimeException("AI Provider returned null");
            rawJson = extractJson(rawJson);
        } catch (Exception e) {
            System.err.println("⚠️ AI Coding Evaluation Failed. Using fallback scoring.");
        }

        try {
            Double codeQuality = 70.0;
            Double timeComplexity = 70.0;
            Double spaceComplexity = 70.0;
            Double style = 70.0;
            String strengths = "Code executed successfully.";
            String weaknesses = "AI evaluation unavailable.";
            String optimizationSuggestions = "Ensure code is optimized for edge cases.";

            if (rawJson != null) {
                Map<String, Object> evalMap = objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
                codeQuality = asDouble(evalMap.get("codeQuality"), 70.0);
                timeComplexity = asDouble(evalMap.get("timeComplexity"), 70.0);
                spaceComplexity = asDouble(evalMap.get("spaceComplexity"), 70.0);
                style = asDouble(evalMap.get("style"), 70.0);
                strengths = asString(evalMap.get("strengths"));
                weaknesses = asString(evalMap.get("weaknesses"));
                optimizationSuggestions = asString(evalMap.get("optimizationSuggestions"));
            }

            codingResult.setCodeQualityScore(codeQuality);
            codingResult.setTimeComplexityScore(timeComplexity);
            codingResult.setSpaceComplexityScore(spaceComplexity);
            codingResult.setStyleScore(style);
            codingResult.setStrengths(strengths);
            codingResult.setWeaknesses(weaknesses);
            codingResult.setOptimizationSuggestions(optimizationSuggestions);

            // Calculate final score using user's formula
            double passRate = 0.0;
            if (codingResult.getTotalTests() != null && codingResult.getTotalTests() > 0) {
                passRate = (codingResult.getPassedTests() * 100.0) / codingResult.getTotalTests();
            }

            double finalScore = Math.round(
                0.40 * passRate +
                0.20 * codeQuality +
                0.15 * timeComplexity +
                0.15 * spaceComplexity +
                0.10 * style
            );

            codingResult.setFinalScore(Math.min(100.0, Math.max(0.0, finalScore)));

            return codingResultRepository.save(codingResult);
        } catch (Exception e) {
            throw new RuntimeException("Failed to evaluate submission: " + e.getMessage(), e);
        }
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

    private String asString(Object o) {
        if (o instanceof String s) return s;
        if (o != null) return o.toString();
        return "";
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
        }
        return cleaned.trim();
    }

    private java.util.Map<String, Object> validateAndCleanCodingData(java.util.Map<String, Object> data, String difficulty) {
        if (data == null) {
            log.error("[CODING_QUESTION_VALIDATE] data map is null — using all defaults");
            data = new java.util.HashMap<>();
        }

        java.util.Map<String, Object> cleanData = new java.util.HashMap<>(data);

        // Required: title
        Object rawTitle = cleanData.get("title");
        if (rawTitle == null || rawTitle.toString().isBlank()) {
            log.warn("[CODING_QUESTION_VALIDATE] 'title' missing or blank — using default");
            cleanData.put("title", "Coding Challenge");
        }

        // Required: description (NOT NULL in DB)
        Object rawDesc = cleanData.get("description");
        if (rawDesc == null || rawDesc.toString().isBlank()) {
            log.warn("[CODING_QUESTION_VALIDATE] 'description' missing or blank — using fallback");
            cleanData.put("description",
                "Solve the following coding problem efficiently.\n\n" +
                "Read the problem statement carefully, implement the required function, " +
                "and test your solution against the provided examples before submitting.");
        }

        // Optional with fallback: constraints
        if (cleanData.get("constraints") == null || cleanData.get("constraints").toString().isBlank())
            cleanData.put("constraints", "No specific constraints provided.");

        // Required: languageSupport
        if (cleanData.get("languageSupport") == null || cleanData.get("languageSupport").toString().isBlank()) {
            log.warn("[CODING_QUESTION_VALIDATE] 'languageSupport' missing — defaulting to java,python,javascript,c++");
            cleanData.put("languageSupport", "java,python,javascript,c++");
        }

        if (cleanData.get("timeLimit") == null)   cleanData.put("timeLimit", 2);
        if (cleanData.get("memoryLimit") == null) cleanData.put("memoryLimit", 128000);

        // Required: starterCode (must be a Map)
        Object sc = cleanData.get("starterCode");
        if (!(sc instanceof java.util.Map)) {
            log.warn("[CODING_QUESTION_VALIDATE] 'starterCode' missing or wrong type — using default stubs");
            cleanData.put("starterCode", java.util.Map.of(
                "java",       "class Solution {\n    public void solve() {\n        // Write your code here\n    }\n}",
                "python",     "def solve():\n    pass",
                "javascript", "function solve() {\n    // Write your code here\n}",
                "c++",        "class Solution {\npublic:\n    void solve() {\n        // Write your code here\n    }\n};"
            ));
        }

        // Warn if testCases absent
        Object tcs = cleanData.get("testCases");
        if (!(tcs instanceof java.util.List<?> list) || list.isEmpty()) {
            log.warn("[CODING_QUESTION_VALIDATE] 'testCases' missing or empty from AI response");
        }

        log.debug("[CODING_QUESTION_VALIDATE] cleaned keys={}", cleanData.keySet());
        return cleanData;
    }

    /** Returns the string value of {@code o} or {@code fallback} if blank/null. */
    private String requireNonBlank(Object o, String fallback) {
        if (o == null) return fallback;
        String s = o.toString().strip();
        return s.isBlank() ? fallback : s;
    }

    private Integer parseInteger(Object obj, int defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Boolean parseBoolean(Object obj, boolean defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Boolean) return (Boolean) obj;
        return Boolean.parseBoolean(obj.toString());
    }
}
