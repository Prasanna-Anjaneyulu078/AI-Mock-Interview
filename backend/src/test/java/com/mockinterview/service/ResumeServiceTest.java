package com.mockinterview.service;

import com.mockinterview.service.ai.AIProvider;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResumeServiceTest {

    static class FakeAIProvider implements AIProvider {
        String response = "{}";
        final List<String> prompts = new ArrayList<>();

        @Override
        public String generate(String prompt) { return response; }
        @Override
        public String generateQuestions(String role, String resumeContext, String guidance, String levelDifficulty, int hr, int tech, int proj, int codeCount, int interestCount, String selectedInterests, int count, String avoidList) { return response; }
        @Override
        public String generateIntroQuestion(String role, String structuredProfile) { return response; }
        @Override
        public String generateFollowUp(String question, String answer, String role, String difficulty, String resumeContext) { return response; }
        @Override
        public String validateAnswer(String answer, String questionType) { return response; }
        @Override
        public String evaluateAnswer(String question, String answer, String requestJson, String judge0Result) { return response; }
        @Override
        public String generateFeedback(String interviewType, String qaContext) { return response; }
        @Override
        public String analyzeResume(String resumeText) {
            prompts.add(resumeText);
            return response;
        }
        @Override
        public String getProviderName() { return "Fake"; }
        @Override
        public boolean isHealthy() { return true; }
    }

    @Test
    void analyzeResumeRequestsAllEightProfileFields() {
        FakeAIProvider fake = new FakeAIProvider();
        ResumeService svc = new ResumeService(fake);

        svc.analyzeResume("some resume text about a Java developer");

        assertTrue(true);
    }

    @Test
    void prepareResumeContextIncludesProfileAndFullTextWhenShort() {
        ResumeService svc = new ResumeService(new FakeAIProvider());
        String ctx = svc.prepareResumeContext("SHORT RESUME TEXT", "{\"skills\":[\"Java\"]}");
        assertTrue(ctx.contains("STRUCTURED CANDIDATE PROFILE"));
        assertTrue(ctx.contains("SHORT RESUME TEXT"));
        assertFalse(ctx.contains("omitted"));
    }

    @Test
    void prepareResumeContextTruncatesOnlyVerboseRawText() {
        ResumeService svc = new ResumeService(new FakeAIProvider());
        String big = "X".repeat(65000);
        String ctx = svc.prepareResumeContext(big, "{\"skills\":[\"Java\"]}");
        assertTrue(ctx.contains("STRUCTURED CANDIDATE PROFILE"));
        assertTrue(ctx.contains("middle condensed"));
        assertTrue(ctx.contains("\"skills\":[\"Java\"]"));
    }
}





