package com.mockinterview.service;

import com.mockinterview.service.ai.AIProvider;

import com.mockinterview.entity.Interview;
import com.mockinterview.entity.Question;
import com.mockinterview.entity.User;
import com.mockinterview.repository.InterviewRepository;
import com.mockinterview.repository.QuestionRepository;
import com.mockinterview.repository.UserRepository;
import com.mockinterview.repository.QuestionBankRepository;
import com.mockinterview.repository.CodingQuestionBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class PersonalizedQuestionServiceTest {

    @Autowired
    QuestionRepository questionRepository;
    @Autowired
    InterviewRepository interviewRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    QuestionBankRepository questionBankRepository;
    @Autowired
    CodingQuestionBankRepository codingQuestionBankRepository;

    static class FakeAIProvider implements AIProvider {
        String response = "{}";
        final List<String> prompts = new ArrayList<>();

        @Override
        public String generate(String prompt) { return response; }
        
        @Override
        public String generateQuestions(String interviewMode, String role, String resumeContext, String guidance, String levelDifficulty, int hr, int tech, int proj, int codeCount, int interestCount, String selectedInterests, int count, String avoidList) {
            prompts.add(role + guidance + levelDifficulty);
            return response;
        }

        @Override
        public String generateIntroQuestion(String role, String structuredProfile) { 
            prompts.add(structuredProfile);
            return response; 
        }
        @Override
        public String generateFollowUp(String question, String answer, String role, String difficulty, String resumeContext) { return response; }
        @Override
        public String validateAnswer(String answer, String questionType) { return response; }
        @Override
        public String evaluateAnswer(String question, String answer, String requestJson, String judge0Result) { return response; }
        @Override
        public String generateFeedback(String interviewType, String qaContext) { return response; }
        @Override
        public String analyzeResume(String resumeText) { return response; }
        @Override
        public String getProviderName() { return "Fake"; }
        @Override
        public boolean isHealthy() { return true; }
    }

    private FakeAIProvider fake;
    private PersonalizedQuestionService svc;
    private Interview interview;
    private Long userId;

    private static final String PROFILE = "{\"skills\":[\"Java\",\"Spring Boot\"],\"projects\":[\"Placement Management System\"]}";

    @BeforeEach
    void setup() {
        fake = new FakeAIProvider();
        svc = new PersonalizedQuestionService(
            fake, 
            questionRepository, 
            new ResumeService(fake), 
            interviewRepository, 
            new LocalCodingQuestionProvider(codingQuestionBankRepository),
            questionBankRepository
        );
        User u = userRepository.save(User.builder()
                .fullName("Test").email("test@example.com").password("p").build());
        userId = u.getId();
        interview = interviewRepository.save(Interview.builder()
                .user(u).resumeId(20L).status("in_progress").build());
    }

    private static String q(String question, String category) {
        return "{\"question\":\"" + question + "\",\"category\":\"" + category +
                "\",\"difficulty\":\"Hard\",\"isCodeQuestion\":false," +
                "\"idealAnswer\":\"a\",\"explanation\":\"e\"}";
    }

    @Test
    void generatesQuestionsWithNewSchemaAndOverridesDifficultyToLevel() {
        fake.response = "[" +
                q("How did you implement JWT auth in your Placement Management System using Spring Boot?", "project") + "," +
                q("Explain Spring Boot auto-configuration.", "technical") + "," +
                q("What challenges did you face building the Placement Management System?", "behavioral") +
                "]";

        svc.generateAndSaveAIQuestions(interview, "Java Developer", "Java Spring Boot resume", PROFILE, 0, 3, 0, 0, 0, "STARTER", userId, 20L, "MIXED");

        List<Question> saved = questionRepository.findAll();
        // Phase 5: STARTER level requires at least 1 coding question; if AI didn't produce one, it's added as fallback
        // So saved.size() >= 3 (could be 3 or more if coding fallback is added)
        assertTrue(saved.size() >= 3, "Should have at least 3 questions, got: " + saved.size());
        // AI-generated questions should have correct difficulty
        saved.stream()
             .filter(qu -> Boolean.TRUE.equals(qu.getGeneratedByAI()))
             .forEach(qu -> assertEquals("Easy", qu.getDifficulty(), "difficulty must match the STARTER level"));
        assertTrue(saved.stream().anyMatch(qu -> "project".equals(qu.getType())),
                "a project question should be generated");
        assertTrue(fake.prompts.get(0).contains("Java Developer"));
        assertTrue(fake.prompts.get(0).contains("Easy"));
    }

    @Test
    void dedupRemovesNearDuplicateWithinBatch() {
        fake.response = "[" +
                q("Explain Spring Boot auto-configuration.", "technical") + "," +
                q("Can you explain Spring Boot auto-configuration?", "technical") + "," +
                q("How did you implement JWT auth in your Placement Management System?", "project") +
                "]";

        svc.generateAndSaveAIQuestions(interview, "Java Developer", "resume", PROFILE, 0, 3, 0, 0, 0, "STANDARD", userId, 20L, "MIXED");

        List<Question> saved = questionRepository.findAll();
        // Dedup must collapse the two "Spring Boot auto-configuration" re-phrasings into a
        // SINGLE question, regardless of any generic fallback questions the engine may append
        // to satisfy the requested count. That is what this test actually verifies.
        long autoConfigQuestions = saved.stream()
                .filter(q -> !Boolean.TRUE.equals(q.getIsCodeQuestion())
                        && q.getQuestionText() != null
                        && q.getQuestionText().toLowerCase().contains("auto-configuration"))
                .count();
        assertEquals(1, autoConfigQuestions, "near-duplicate auto-configuration question should be dropped, leaving exactly one");
    }

    @Test
    void dedupRemovesQuestionsFromPastInterviews() {
        questionRepository.save(Question.builder()
                .interview(interview)
                .questionText("How did you implement JWT auth in your Placement Management System?")
                .build());

        fake.response = "[" +
                q("How did you implement JWT auth in your Placement Management System?", "project") + "," +
                q("What trade-offs did you consider when designing the Placement Management System?", "project") +
                "]";

        svc.generateAndSaveAIQuestions(interview, "Java Developer", "resume", PROFILE, 0, 2, 0, 0, 0, "STANDARD", userId, 20L, "MIXED");

        List<Question> saved = questionRepository.findAll();
        // saved includes: the pre-existing past question (1) + 1 new non-duplicate + coding fallbacks (2) = 4
        // The important assertion is that the past duplicate was NOT added again
        long distinctTexts = saved.stream().map(Question::getQuestionText).distinct().count();
        assertEquals(saved.size(), distinctTexts, "no duplicate question text should exist in saved questions");
        // And we have the new non-duplicate question
        assertTrue(saved.stream().anyMatch(q -> q.getQuestionText() != null &&
                q.getQuestionText().contains("trade-offs")), "new unique question should be saved");
    }

    @Test
    void introQuestionUsesGeminiWhenAvailable() {
        fake.response = "Tell me about your Spring Boot backend experience.";
        String intro = svc.generateIntroQuestion("Java Developer", PROFILE);
        assertEquals("Tell me about your Spring Boot backend experience.", intro);
    }

    @Test
    void introQuestionFallsBackToRoleTemplate() {
        fake.response = "";
        String intro = svc.generateIntroQuestion("Java Developer", PROFILE);
        assertTrue(intro.toLowerCase().contains("spring boot"), "fallback intro should be role-aware for Java");
    }

    /**
     * Core CODING-pipeline fix: even when the AI returns a MIX of behavioral / technical /
     * HR questions, a CODING interview must persist ONLY coding questions. The
     * PersonalizedQuestionService coerces every element to type="coding" and
     * isCodeQuestion=true, so no MCQ / behavioral / HR question can slip through.
     */
    @Test
    void codingMode_ForcesAllQuestionsToCoding_EvenWhenAiReturnsMixed() {
        fake.response = "[" +
                q("Tell me about a conflict you resolved at work.", "behavioral") + "," +
                q("Explain the difference between TCP and UDP.", "technical") + "," +
                q("What is your greatest weakness?", "hr") +
                "]";

        svc.generateAndSaveAIQuestions(interview, "Java Developer", "resume", PROFILE, 0, 0, 0, 3, 0, "STANDARD", userId, 20L, "CODING");

        List<Question> saved = questionRepository.findAll();
        assertEquals(3, saved.size(), "all three AI questions should be saved (coerced to coding)");
        for (Question qu : saved) {
            assertTrue(qu.getIsCodeQuestion(), "CODING mode: every question must be a coding question");
            assertEquals("coding", qu.getType(), "CODING mode: every question type must be 'coding'");
        }
    }

    /**
     * CODING fallback (provider failure) must produce ONLY coding problems — never
     * MCQ / behavioral / HR. Exercises the previously compile-broken
     * forceSaveFallbackQuestions CODING branch.
     */
    @Test
    void forceSaveFallbackQuestions_CodingMode_ProducesOnlyCoding() {
        for (int i = 0; i < 5; i++) {
            com.mockinterview.entity.CodingQuestionBank cqb = new com.mockinterview.entity.CodingQuestionBank();
            cqb.setTitle("Title " + i);
            cqb.setDescription("Description");
            cqb.setRole("cs_fundamentals");
            cqb.setDifficulty("MEDIUM");
            codingQuestionBankRepository.save(cqb);
        }

        Set<String> seen = new HashSet<>();
        svc.forceSaveFallbackQuestions(interview, 5, seen, "CODING");

        List<Question> saved = questionRepository.findAll();
        assertEquals(5, saved.size(), "should generate 5 coding fallback questions");
        for (Question qu : saved) {
            assertTrue(qu.getIsCodeQuestion(), "CODING fallback must be a coding question");
            assertEquals("coding", qu.getType(), "CODING fallback type must be 'coding'");
        }
    }
}

