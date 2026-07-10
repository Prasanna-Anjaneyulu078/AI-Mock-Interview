package com.mockinterview.service.ai;

/**
 * Abstraction over an AI backend used by the interview platform. Implementations include
 * cloud providers (OpenRouter, Groq, Ollama) and a local rule-based engine.
 *
 * <p>Every method is implemented by both the cloud providers (which build a prompt and
 * call the model) and the local engine (which applies deterministic rules). The
 * {@link AIProviderRouter} fans out across providers with failover, ending at the local
 * engine, so the interview always completes.</p>
 *
 * <p>All scoring/feedback JSON returned by these methods uses the EXACT field names the
 * service layer parses ({@code ScoringService}, {@code InterviewService}, {@code ResumeService}).</p>
 */
public interface AIProvider {

    /** Generic raw completion (cloud providers call the model; local returns null). */
    String generate(String prompt);

    /**
     * Generates a batch of questions based on counts for different categories.
     */
    String generateQuestions(String role, String resumeContext, String guidance, String levelDifficulty,
                             int hr, int tech, int proj, int codeCount, int interestCount, String selectedInterests, int count, String avoidList);

    /** Role/student-aware opening question. */
    String generateIntroQuestion(String role, String structuredProfile);

    /** Adaptive follow-up generation. Returns a JSON array of {"question","rationale"}. */
    String generateFollowUp(String question, String answer, String role, String difficulty, String resumeContext);

    /** Answer-spam validation. Returns the bare word VALID or INVALID. */
    String validateAnswer(String answer, String questionType);

    /**
     * Per-answer evaluation. Returns JSON with: score, technicalAccuracy, communication,
     * problemSolving, codeQuality, isCorrect, feedback, improvementSuggestions,
     * answerComparison, strengths, weaknesses, recommendations.
     */
    String evaluateAnswer(String question, String answer, String requestJson, String judge0Result);

    /**
     * Final interview feedback. Returns JSON with: overallScore, categoryScores
     * (communicationSkills, technicalKnowledge, problemSolving, codeQuality, confidence),
     * strengths, areasOfImprovement, recommendations, finalAssessment, evaluated.
     */
    String generateFeedback(String interviewType, String qaContext);

    /** Resume -> structured profile with fields: skills, technologies, frameworks, languages,
     * projects, education, experience, certifications, achievements, domainsOfExpertise. */
    String analyzeResume(String resumeText);

    /** Human-readable name, e.g. "OpenRouter". */
    String getProviderName();

    /** Whether this provider can currently serve requests. */
    boolean isHealthy();
}
