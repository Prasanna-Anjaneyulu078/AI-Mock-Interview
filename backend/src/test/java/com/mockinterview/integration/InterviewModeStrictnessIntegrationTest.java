package com.mockinterview.integration;

import com.mockinterview.entity.Question;
import com.mockinterview.entity.User;
import com.mockinterview.exception.ValidationException;
import com.mockinterview.repository.QuestionRepository;
import com.mockinterview.repository.UserRepository;
import com.mockinterview.service.InterviewService;
import com.mockinterview.service.ai.AIProviderRouter;
import com.mockinterview.dto.InterviewRequest;
import com.mockinterview.dto.InterviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class InterviewModeStrictnessIntegrationTest {

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @MockBean
    private AIProviderRouter aiProviderRouter;

    private User testUser;

    @BeforeEach
    public void setup() {
        testUser = User.builder()
                .fullName("strict_test_user")
                .email("strict@test.com")
                .password("password")
                .build();
        userRepository.save(testUser);

        // Deterministic, offline AI stub. For CODING it deliberately returns a MIX of
        // categories to prove the mode-coercion forces EVERY question to coding.
        when(aiProviderRouter.generateIntroQuestion(anyString(), anyString()))
                .thenReturn("Tell me about yourself.");
        when(aiProviderRouter.generateQuestions(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyString(), anyInt(), anyString()))
                .thenAnswer(inv -> {
                    String mode = inv.getArgument(0);
                    int count = Math.max(1, inv.getArgument(11));
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < count; i++) {
                        if (i > 0) sb.append(",");
                        if (mode != null && mode.contains("CODING")) {
                            // Alternate categories so coercion is actually exercised.
                            String cat = (i % 2 == 0) ? "behavioral" : "technical";
                            sb.append(q("Coding probe " + i + " (" + cat + ")", cat));
                        } else if (mode != null && mode.contains("BEHAVIORAL")) {
                            sb.append(q("Behavioral probe " + i, "behavioral"));
                        } else {
                            sb.append(q("Mixed probe " + i, (i % 2 == 0) ? "behavioral" : "technical"));
                        }
                    }
                    sb.append("]");
                    return sb.toString();
                });
    }

    private static String q(String text, String category) {
        return "{\"question\":\"" + text + "\",\"category\":\"" + category
                + "\",\"difficulty\":\"Easy\",\"isCodeQuestion\":false,"
                + "\"idealAnswer\":\"a\",\"explanation\":\"e\"}";
    }

    @Test
    @Transactional
    public void testCodingInterview_OnlyGeneratesCodingQuestions() {
        InterviewRequest request = new InterviewRequest();
        request.setRole("Java Developer");
        request.setInterviewMode("CODING_INTERVIEW");
        request.setTargetQuestionCount(5);
        request.setCodingLanguage("java");
        
        InterviewResponse response = interviewService.startInterview(testUser.getId(), request);
        
        assertEquals(5, response.getQuestions().size(), "Should generate exactly 5 questions");
        
        List<Question> questions = questionRepository.findByInterviewId(Long.parseLong(response.getInterviewId()));
        for (Question q : questions) {
            assertTrue(q.getIsCodeQuestion(), "Question must be a coding question");
            assertEquals("coding", q.getType(), "Category must be coding");
        }
    }

    @Test
    @Transactional
    public void testMixedInterview_GeneratesBalancedDistribution() {
        InterviewRequest request = new InterviewRequest();
        request.setRole("Full Stack");
        request.setInterviewMode("MIXED");
        request.setTargetQuestionCount(5);
        
        InterviewResponse response = interviewService.startInterview(testUser.getId(), request);
        
        assertEquals(5, response.getQuestions().size(), "Should generate exactly 5 questions");
        
        // MIXED should skip Intro question (wait, MIXED doesn't skip intro, so batch is 4.
        // If target is 5, intro is 1, batch=4. 4/5 = 0 -> slice = 1.
        // It will generate 1 hr, 1 tech, 1 proj, 1 code, 0 interest.
        // So total is 1+4 = 5 questions!
        
        List<Question> questions = questionRepository.findByInterviewId(Long.parseLong(response.getInterviewId()));
        long codeCount = questions.stream().filter(q -> Boolean.TRUE.equals(q.getIsCodeQuestion())).count();
        long behavioralCount = questions.stream().filter(q -> "behavioral".equals(q.getType())).count();
        
        assertTrue(codeCount > 0, "Mixed should contain at least 1 coding question");
        assertTrue(behavioralCount > 0, "Mixed should contain at least 1 behavioral question (intro)");
    }

    @Test
    @Transactional
    public void testResumeBasedInterview_RequiresResume() {
        InterviewRequest request = new InterviewRequest();
        request.setRole("Software Engineer");
        request.setInterviewMode("RESUME_BASED");
        request.setTargetQuestionCount(5);
        // No resumeId supplied -> must be rejected server-side.

        assertThrows(ValidationException.class, () ->
                interviewService.startInterview(testUser.getId(), request),
                "Resume-Based interview must fail without an uploaded resume");
    }

    @Test
    @Transactional
    public void testBehavioralInterview_WorksWithoutResume() {
        InterviewRequest request = new InterviewRequest();
        request.setRole("Team Lead");
        request.setInterviewMode("BEHAVIORAL");
        request.setTargetQuestionCount(3);
        // No resumeId -> allowed; behavioral questions are resume-optional.

        InterviewResponse response = interviewService.startInterview(testUser.getId(), request);
        assertEquals(3, response.getQuestions().size(), "Should generate exactly 3 questions");
        List<Question> questions = questionRepository.findByInterviewId(Long.parseLong(response.getInterviewId()));
        for (Question q : questions) {
            assertEquals("behavioral", q.getType(), "Category must be behavioral");
        }
    }
}
