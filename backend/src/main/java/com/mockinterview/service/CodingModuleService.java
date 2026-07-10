package com.mockinterview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.service.Judge0Result;
import com.mockinterview.service.ai.AIProvider;
import com.mockinterview.entity.*;
import com.mockinterview.repository.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
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
    public CodingQuestion generateCodingQuestion(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new RuntimeException("Interview not found"));

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
            "    \"java\": \"class Solution { ... }\",\n" +
            "    \"python\": \"def solution(x):\\n    pass\",\n" +
            "    \"javascript\": \"function solution(x) {\\n}\"\n" +
            "  },\n" +
            "  \"timeLimit\": 2,\n" +
            "  \"memoryLimit\": 128000,\n" +
            "  \"testCases\": [\n" +
            "    { \"name\": \"Example 1\", \"input\": \"1 2\", \"expectedOutput\": \"3\", \"isHidden\": false },\n" +
            "    { \"name\": \"Hidden Test 1\", \"input\": \"10 20\", \"expectedOutput\": \"30\", \"isHidden\": true }\n" +
            "  ]\n" +
            "}", interview.getInterviewType(), interview.getDifficulty(), interview.getResumeText(), interview.getSelectedInterests()
        );

        String rawJson = aiProvider.generate(prompt);
        rawJson = extractJson(rawJson);

        try {
            Map<String, Object> data = objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
            
            CodingQuestion q = CodingQuestion.builder()
                    .interview(interview)
                    .title((String) data.get("title"))
                    .description((String) data.get("description"))
                    .constraints((String) data.get("constraints"))
                    .difficulty(interview.getDifficulty())
                    .languageSupport((String) data.get("languageSupport"))
                    .starterCode(objectMapper.writeValueAsString(data.get("starterCode")))
                    .timeLimit((Integer) data.get("timeLimit"))
                    .memoryLimit((Integer) data.get("memoryLimit"))
                    .build();

            q = codingQuestionRepository.save(q);

            List<Map<String, Object>> tcData = (List<Map<String, Object>>) data.get("testCases");
            if (tcData != null) {
                for (Map<String, Object> tc : tcData) {
                    CodingTestCase codingTestCase = CodingTestCase.builder()
                            .codingQuestion(q)
                            .name((String) tc.get("name"))
                            .input((String) tc.get("input"))
                            .expectedOutput((String) tc.get("expectedOutput"))
                            .isHidden((Boolean) tc.get("isHidden"))
                            .build();
                    q.getTestCases().add(codingTestCase);
                }
                q = codingQuestionRepository.save(q);
            }
            return q;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate coding question: " + e.getMessage(), e);
        }
    }

    /**
     * Executes code against visible test cases for "Run Code" functionality without saving.
     */
    public Judge0Result runSampleCode(Long questionId, String sourceCode, String language) {
        CodingQuestion question = codingQuestionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Coding Question not found"));

        List<TestCase> standardTestCases = question.getTestCases().stream()
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
    public CodingSubmission submitCode(Long questionId, String sourceCode, String language) {
        CodingQuestion question = codingQuestionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Coding Question not found"));

        // Convert CodingTestCase to standard TestCase for Judge0Service compatibility
        List<TestCase> standardTestCases = question.getTestCases().stream().map(ctc -> 
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

        if (result != null) {
            CodingResult codingResult = CodingResult.builder()
                    .submission(submission)
                    .passedTests(result.getPassedTests())
                    .failedTests(result.getTotalTests() - result.getPassedTests())
                    .totalTests(result.getTotalTests())
                    .compileOutput(result.getCompileOutput())
                    .stdout(result.getStdout())
                    .stderr(result.getStderr())
                    .build();
            codingResultRepository.save(codingResult);
        }

        return submission;
    }

    /**
     * Evaluates a submission using the 40/20/15/15/10 weighted formula.
     */
    public CodingResult evaluateSubmission(Long submissionId) {
        CodingSubmission submission = codingSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        CodingResult codingResult = codingResultRepository.findBySubmissionId(submissionId)
                .orElseThrow(() -> new RuntimeException("Coding Result not found"));

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

        String rawJson = aiProvider.generate(prompt);
        rawJson = extractJson(rawJson);

        try {
            Map<String, Object> evalMap = objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
            
            Double codeQuality = asDouble(evalMap.get("codeQuality"), 70.0);
            Double timeComplexity = asDouble(evalMap.get("timeComplexity"), 70.0);
            Double spaceComplexity = asDouble(evalMap.get("spaceComplexity"), 70.0);
            Double style = asDouble(evalMap.get("style"), 70.0);

            codingResult.setCodeQualityScore(codeQuality);
            codingResult.setTimeComplexityScore(timeComplexity);
            codingResult.setSpaceComplexityScore(spaceComplexity);
            codingResult.setStyleScore(style);
            codingResult.setStrengths(asString(evalMap.get("strengths")));
            codingResult.setWeaknesses(asString(evalMap.get("weaknesses")));
            codingResult.setOptimizationSuggestions(asString(evalMap.get("optimizationSuggestions")));

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
}
