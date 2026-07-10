package com.mockinterview.service;

import com.mockinterview.service.ai.AIProvider;

import com.mockinterview.entity.Interview;
import com.mockinterview.entity.Question;
import com.mockinterview.entity.User;
import com.mockinterview.repository.InterviewRepository;
import com.mockinterview.repository.QuestionRepository;
import com.mockinterview.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PersonalizedQuestionServiceTest {

    @Autowired
    QuestionRepository questionRepository;
    @Autowired
    InterviewRepository interviewRepository;
    @Autowired
    UserRepository userRepository;

    static class FakeAIProvider implements AIProvider {
        String response = "{}";
        final List<String> prompts = new ArrayList<>();

        @Override
        public String generate(String prompt) { return response; }
        
        @Override
        public String generateQuestions(String role, String resumeContext, String guidance, String levelDifficulty, int hr, int tech, int proj, int count, String avoidList) {
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
        svc = new PersonalizedQuestionService(fake, questionRepository, new ResumeService(fake));
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

        svc.generateAndSaveAIQuestions(interview, "Java Developer", "Java Spring Boot resume", PROFILE, 3, "STARTER", userId, 20L);

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

        svc.generateAndSaveAIQuestions(interview, "Java Developer", "resume", PROFILE, 3, "STANDARD", userId, 20L);

        List<Question> saved = questionRepository.findAll();
        // Phase 5: 2 unique questions + at least 2 fallback coding = at least 4
        // But dedup should still remove the near-duplicate; the unique non-code questions should be 2
        long uniqueNonCode = saved.stream().filter(q -> !Boolean.TRUE.equals(q.getIsCodeQuestion())).count();
        assertEquals(2, uniqueNonCode, "near-duplicate should be dropped, 2 unique non-code questions remain");
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

        svc.generateAndSaveAIQuestions(interview, "Java Developer", "resume", PROFILE, 2, "STANDARD", userId, 20L);

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
}

