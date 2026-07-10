package com.mockinterview.service;

import com.mockinterview.dto.AnswerRequest;
import com.mockinterview.entity.Answer;
import com.mockinterview.entity.Question;
import com.mockinterview.service.ai.AIProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10: Unit tests for ScoringService.
 * Verifies:
 * - Empty answers are scored as 0 (Phase 3)
 * - Unanswered markers are scored as 0 (Phase 3)
 * - AI evaluation is called for non-empty answers
 * - Weighted coding score formula (Phase 6)
 */
class ScoringServiceTest {

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        AIProvider fakeAI = new AIProvider() {
            @Override public String generate(String prompt) { return null; }
            @Override public String generateQuestions(String role, String resumeContext, String guidance,
                    String levelDifficulty, int hr, int tech, int proj, int codeCount, int interestCount, String selectedInterests, int count, String avoidList) { return "[]"; }
            @Override public String generateIntroQuestion(String role, String structuredProfile) { return "Tell me about yourself."; }
            @Override public String generateFollowUp(String question, String answer, String role, String difficulty, String resumeContext) { return "[]"; }
            @Override public String validateAnswer(String answer, String questionType) { return "VALID"; }
            @Override public String evaluateAnswer(String question, String answer, String requestJson, String judge0Result) {
                // Return a valid scored JSON for non-empty answers
                return "{\"evaluationScore\": 75, \"technicalScore\": 80, \"communicationScore\": 70, "
                     + "\"problemSolvingScore\": 75, \"codeQualityScore\": 72, "
                     + "\"feedback\": \"Good answer.\", \"strengths\": [], \"weaknesses\": [], \"recommendations\": []}";
            }
            @Override public String generateFeedback(String interviewType, String qaContext) { return "{}"; }
            @Override public String analyzeResume(String resumeText) { return "{}"; }
            @Override public String getProviderName() { return "Fake"; }
            @Override public boolean isHealthy() { return true; }
        };
        scoringService = new ScoringService(fakeAI);
    }

    // ─── Phase 3: Empty Answer Tests ─────────────────────────────────────────

    @Test
    @DisplayName("Phase 3: Null answerText → score = 0, feedback = 'Question not answered'")
    void nullAnswer_scoredAsSkipped() {
        Answer answer = new Answer();
        Question question = new Question();
        question.setType("technical");

        AnswerRequest request = new AnswerRequest();
        request.setAnswerText(null);

        scoringService.evaluateAnswer(answer, question, request);

        assertEquals(0.0, answer.getEvaluationScore(), "Null answer must score 0");
        assertTrue(answer.getFeedback().contains("not answered"), "Feedback must mention not answered");
    }

    @Test
    @DisplayName("Phase 3: Blank answerText → score = 0")
    void blankAnswer_scoredAsSkipped() {
        Answer answer = new Answer();
        Question question = new Question();
        AnswerRequest request = new AnswerRequest();
        request.setAnswerText("   ");

        scoringService.evaluateAnswer(answer, question, request);

        assertEquals(0.0, answer.getEvaluationScore(), "Blank answer must score 0");
    }

    @Test
    @DisplayName("Phase 3: AssemblyAI error marker → score = 0")
    void assemblyAiErrorMarker_scoredAsSkipped() {
        Answer answer = new Answer();
        Question question = new Question();
        AnswerRequest request = new AnswerRequest();
        request.setAnswerText("[No speech detected]");

        scoringService.evaluateAnswer(answer, question, request);

        assertEquals(0.0, answer.getEvaluationScore(), "Speech detection failure must score 0");
    }

    @Test
    @DisplayName("Phase 3: Real answer text → AI evaluation called, score > 0")
    void realAnswer_scoredByAI() {
        Answer answer = new Answer();
        Question question = new Question();
        question.setType("technical");
        AnswerRequest request = new AnswerRequest();
        request.setAnswerText("Spring Boot uses auto-configuration and embedded servers to simplify Java application setup.");

        scoringService.evaluateAnswer(answer, question, request);

        assertNotNull(answer.getEvaluationScore(), "Real answer must have a score");
        assertTrue(answer.getEvaluationScore() > 0, "Real answer score must be > 0");
    }

    // ─── Phase 6: Weighted Scoring Formula Tests ──────────────────────────────

    @Test
    @DisplayName("Phase 6: Judge0 result blends scores with weighted formula")
    void judge0Result_appliesWeightedFormula() {
        Answer answer = new Answer();
        answer.setEvaluationScore(80.0);
        answer.setCodeQualityScore(90.0);
        answer.setCommunicationScore(70.0);

        Question question = new Question();
        question.setType("coding");

        AnswerRequest request = new AnswerRequest();
        request.setCode("def solution(): pass");
        request.setLanguage("python");

        // 5/5 tests passed → passRate = 100%
        Judge0Result judge0 = new Judge0Result();
        judge0.setPassed(true);
        judge0.setPassedTests(5);
        judge0.setTotalTests(5);

        scoringService.evaluateAnswer(answer, question, request, judge0);

        // AI evaluateAnswer sets score=75 first; then judge0 blend:
        // 0.40*100 + 0.25*72 + 0.15*75 + 0.10*75 + 0.10*70 = 40+18+11.25+7.5+7 = 83.75 → 84
        assertNotNull(answer.getEvaluationScore(), "Score must be set");
        assertTrue(answer.getEvaluationScore() >= 80.0, "High pass rate should yield high score: " + answer.getEvaluationScore());
        assertTrue(answer.getFeedback().contains("test cases"), "Feedback must mention test cases");
    }

    @Test
    @DisplayName("Phase 6: All tests fail → score heavily penalized")
    void judge0AllFail_lowScore() {
        Answer answer = new Answer();
        answer.setEvaluationScore(60.0);
        answer.setCodeQualityScore(60.0);
        answer.setCommunicationScore(60.0);

        Question question = new Question();
        AnswerRequest request = new AnswerRequest();
        request.setCode("def wrong(): return -1");
        request.setLanguage("python");

        Judge0Result judge0 = new Judge0Result();
        judge0.setPassed(false);
        judge0.setPassedTests(0);
        judge0.setTotalTests(5);

        scoringService.evaluateAnswer(answer, question, request, judge0);

        // AI score = 60, then blended:
        // 0.40*0 + 0.25*60 + 0.15*60 + 0.10*60 + 0.10*60 = 0+15+9+6+6 = 36
        // But evaluateAnswer is called first setting score=75 from fake AI, then:
        // 0.40*0 + 0.25*60 + 0.15*75 + 0.10*75 + 0.10*60 = 0+15+11.25+7.5+6 = 39.75 → 40... or ~43 depending on initial
        // Either way it should be significantly below 60 (the neutral fallback)
        assertTrue(answer.getEvaluationScore() <= 55.0, "Zero pass rate should yield a low score: " + answer.getEvaluationScore());
    }

    @Test
    @DisplayName("Phase 6: Score clamped to 0-100 range")
    void score_clampedToValidRange() {
        Answer answer = new Answer();
        answer.setEvaluationScore(100.0);
        answer.setCodeQualityScore(100.0);
        answer.setCommunicationScore(100.0);

        Question question = new Question();
        AnswerRequest request = new AnswerRequest();
        request.setCode("perfect solution");
        request.setLanguage("python");

        Judge0Result judge0 = new Judge0Result();
        judge0.setPassed(true);
        judge0.setPassedTests(10);
        judge0.setTotalTests(10);

        scoringService.evaluateAnswer(answer, question, request, judge0);

        assertTrue(answer.getEvaluationScore() <= 100.0, "Score must never exceed 100");
        assertTrue(answer.getEvaluationScore() >= 0.0, "Score must never be negative");
    }
}
