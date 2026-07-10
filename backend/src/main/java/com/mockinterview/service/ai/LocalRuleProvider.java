package com.mockinterview.service.ai;

import org.springframework.stereotype.Service;


@Service
public class LocalRuleProvider implements AIProvider {

    @Override
    public String generate(String prompt) {
        return null;
    }
    @Override
    public String getProviderName() {
        return "LocalRuleEngine";
    }

    @Override
    public boolean isHealthy() {
        return true; // Always healthy
    }

    @Override
    public String generateQuestions(String role, String resumeContext, String guidance, String levelDifficulty, int hr, int tech, int proj, int codeCount, int interestCount, String selectedInterests, int count, String avoidList) {
        // Fallback static questions
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < count; i++) {
            sb.append("  {\n");
            sb.append("    \"id\": ").append(i + 1).append(",\n");
            sb.append("    \"question\": \"Please describe your experience relevant to this role (Fallback Question ").append(i + 1).append(").\",\n");
            sb.append("    \"type\": \"technical\",\n");
            sb.append("    \"isCodeQuestion\": false\n");
            sb.append("  }");
            if (i < count - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String generateIntroQuestion(String role, String structuredProfile) {
        return "Hi! I see you're applying for " + (role != null ? role : "a role") + ". Could you introduce yourself?";
    }

    @Override
    public String generateFollowUp(String question, String answer, String role, String difficulty, String resumeContext) {
        return "[\n  {\n    \"question\": \"That's interesting. Could you elaborate on how you approached the main challenges in that scenario?\",\n    \"type\": \"followup\"\n  }\n]";
    }

    @Override
    public String validateAnswer(String answer, String questionType) {
        // Simple rule: if it's too short, it's invalid
        if (answer == null || answer.trim().length() < 10) {
            return "INVALID";
        }
        return "VALID";
    }

    @Override
    public String evaluateAnswer(String question, String answer, String requestJson, String judge0Result) {
        // Simple heuristic scoring
        int baseScore = 65;
        if (answer != null && answer.length() > 50) baseScore += 5;
        if (answer != null && answer.length() > 150) baseScore += 5;
        if (answer != null && answer.toLowerCase().contains("because")) baseScore += 5;
        
        // Ensure not greater than 100
        baseScore = Math.min(baseScore, 100);
        
        return "{\n" +
                "  \"evaluationScore\": " + baseScore + ",\n" +
                "  \"technicalScore\": " + baseScore + ",\n" +
                "  \"communicationScore\": " + baseScore + ",\n" +
                "  \"problemSolvingScore\": " + baseScore + ",\n" +
                "  \"codeQualityScore\": 0.0,\n" +
                "  \"strengths\": [\"Answered the question\"],\n" +
                "  \"weaknesses\": [\"Used fallback scoring engine, detail may be lacking\"],\n" +
                "  \"recommendations\": [\"Elaborate more on specific technologies\"],\n" +
                "  \"feedback\": \"Your answer was processed by the fallback engine due to AI unavailability. Good effort!\",\n" +
                "  \"improvementSuggestions\": \"Provide more specific examples.\",\n" +
                "  \"answerComparison\": \"N/A (Fallback Engine)\"\n" +
                "}";
    }

    @Override
    public String generateFeedback(String interviewType, String qaContext) {
        return "{\n" +
                "  \"overallScore\": 75.0,\n" +
                "  \"summary\": \"The interview was completed using the fallback rule engine. The candidate communicated effectively.\",\n" +
                "  \"strengths\": [\"Completed the interview\", \"Maintained composure\"],\n" +
                "  \"weaknesses\": [\"Detailed AI evaluation was unavailable\"],\n" +
                "  \"keyTakeaways\": [\"Good baseline communication skills\"],\n" +
                "  \"improvementPlan\": \"Review specific domain concepts and practice STAR method responses.\"\n" +
                "}";
    }

    @Override
    public String analyzeResume(String resumeText) {
        return "{\n" +
                "  \"skills\": [\"Java\", \"Spring Boot\", \"Communication\"],\n" +
                "  \"technologies\": [\"Git\", \"SQL\"],\n" +
                "  \"projects\": [\"Extracted from resume\"],\n" +
                "  \"education\": [\"Degree\"],\n" +
                "  \"yearsOfExperience\": 3\n" +
                "}";
    }
}




