package com.mockinterview.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Base for cloud LLM providers. Centralises prompt construction (so every provider emits
 * the exact JSON field names the service layer parses) and delegates the actual HTTP call
 * to generateContent. The local engine (LocalRuleProvider) implements the interface directly.
 */
public abstract class AbstractLLMProvider implements AIProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected abstract String generateContent(String prompt);

    @org.springframework.beans.factory.annotation.Autowired
    private com.mockinterview.repository.RoleMetadataRepository roleMetadataRepository;

    private static final Map<String, String> LEVEL_STYLE = Map.of(
            "BEGINNER", "Style: short, direct recall/understanding questions.",
            "INTERMEDIATE", "Style: scenario and applied questions.",
            "ADVANCED", "Style: open-ended design and trade-off discussions."
    );

    private String resolveRoleFocus(String role) {
        if (role == null) return null;
        return roleMetadataRepository.findByRoleName(role.toUpperCase().replace(" ", "_"))
            .map(com.mockinterview.entity.RoleMetadata::getTopicsJson)
            .orElse(null);
    }

    @Override
    public String generateQuestions(String interviewMode, String role, String resumeContext, String guidance, String levelDifficulty,
                                    int hr, int tech, int proj, int codeCount, int interestCount, String selectedInterests, int count, String avoidList) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are InterviewIQ AI, an expert technical interviewer for a ")
          .append(role != null ? role : "Software").append(" candidate at the ")
          .append(levelDifficulty).append(" difficulty level.\n\n");
        sb.append("DIFFICULTY GUIDANCE (").append(levelDifficulty).append("):\n").append(guidance).append("\n");
        sb.append("DIFFICULTY STYLE: ").append(LEVEL_STYLE.getOrDefault(levelDifficulty, "")).append("\n\n");
        String roleFocus = resolveRoleFocus(role);
        if (roleFocus != null) {
            sb.append("ROLE FOCUS (prioritize for a ").append(role).append(" candidate):\n").append(roleFocus).append("\n\n");
        }
        sb.append("CANDIDATE CONTEXT:\n").append(resumeContext == null ? "" : resumeContext).append("\n\n");
        if (avoidList != null && !avoidList.isBlank()) {
            sb.append("DO NOT repeat these questions already used: ").append(avoidList).append("\n\n");
        }
        
        // Strict Interview Mode Enforcement
        if (interviewMode != null) {
            // Normalize legacy aliases so the prompt wording is consistent.
            String effectiveMode = interviewMode;
            switch (interviewMode) {
                case "CODING_INTERVIEW": effectiveMode = "CODING"; break;
                case "RESUME":           effectiveMode = "RESUME_BASED"; break;
                case "HR":               effectiveMode = "BEHAVIORAL"; break;
                default: break;
            }
            sb.append("STRICT INTERVIEW MODE (").append(effectiveMode).append("): ");
            switch (effectiveMode) {
                case "CODING":
                    sb.append("ONLY generate coding/algorithm problems. DO NOT generate MCQ (multiple-choice) questions, behavioral, HR, resume, or generic technical questions. Every question must be a coding challenge.\n\n");
                    break;
                case "TECHNICAL":
                    sb.append("ONLY generate technical conceptual/knowledge questions. DO NOT generate coding challenges, behavioral, or HR questions.\n\n");
                    break;
                case "BEHAVIORAL":
                    sb.append("ONLY generate behavioral (STAR format, leadership, teamwork) questions. DO NOT generate coding or technical questions.\n\n");
                    break;
                case "RESUME_BASED":
                    sb.append("ONLY generate questions referencing the candidate's resume (projects, skills, experience). DO NOT generate random coding or generic behavioral questions.\n\n");
                    break;
                case "HR":
                    sb.append("ONLY generate HR questions (strengths, weaknesses, career goals, fit). DO NOT generate coding or technical questions.\n\n");
                    break;
                case "PROJECT":
                    sb.append("ONLY generate questions about the candidate's projects (architecture, design decisions, challenges). DO NOT generate generic HR or behavioral questions.\n\n");
                    break;

                case "MIXED":
                default:
                    sb.append("Generate a balanced mix of questions as requested by the counts below.\n\n");
                    break;
            }
        }

        sb.append("RULES:\n");
        sb.append("1. Return ONLY a raw JSON array. No markdown, no prose.\n");
        sb.append("2. Generate exactly ").append(hr).append(" behavioral/HR questions (category: \"behavioral\").\n");
        sb.append("3. Generate exactly ").append(tech).append(" technical questions (category: \"technical\").\n");
        sb.append("4. Generate exactly ").append(proj).append(" project/experience questions (category: \"project\").\n");
        sb.append("5. Generate exactly ").append(codeCount).append(" pure coding questions (category: \"coding\").\n");
        if (interestCount > 0 && selectedInterests != null && !selectedInterests.isBlank()) {
            sb.append("6. Generate exactly ").append(interestCount).append(" interest-based technical questions (category: \"technical\"). Focus these ONLY on: ").append(selectedInterests).append(".\n");
        }
        sb.append("7. For NON-coding questions each element MUST have:\n");
        sb.append("   \"question\", \"category\", \"difficulty\" (exactly \"").append(levelDifficulty).append("\"), \"isCodeQuestion\": false, \"idealAnswer\", \"explanation\".\n");
        sb.append("8. For CODING questions each element MUST have:\n");
        sb.append("   \"question\" (short prompt), \"category\": \"coding\", \"difficulty\", \"isCodeQuestion\": true,\n");
        sb.append("   \"title\" (short display name e.g. \"Two Sum\"),\n");
        sb.append("   \"problemDescription\" (full 2-4 sentence problem statement),\n");
        sb.append("   \"exampleInput\" (concrete input example as a string),\n");
        sb.append("   \"exampleOutput\" (expected output for that input),\n");
        sb.append("   \"constraints\" (1-3 bullet constraints e.g. \"1 <= n <= 10^4, -10^9 <= nums[i] <= 10^9\"),\n");
        sb.append("   \"starterCode\" (ONLY the method signature with an empty body stub — NO implementation. Example: `function twoSum(nums, target) { // Write your code here }`),\n");
        sb.append("   \"solutionCode\" (full correct implementation — stored server-side for evaluation only, never shown to candidate),\n");
        sb.append("   \"tags\" (comma-separated topic tags e.g. \"Array, Hash Map\"),\n");
        sb.append("   \"timeComplexity\" (expected optimal e.g. \"O(n)\"),\n");
        sb.append("   \"idealAnswer\" (1-2 sentence explanation of the optimal approach — NOT code),\n");
        sb.append("   \"codeType\": \"write\",\n");
        sb.append("   \"testCases\" (array of 3-5 cases: [{\"input\":\"\",\"expectedOutput\":\"\",\"hidden\":false/true}]).\n");
        sb.append("   IMPORTANT: starterCode must contain ONLY the empty function signature. If it contains any real logic it will be rejected.\n");
        sb.append("9. Reference the candidate's actual skills/projects.\n");
        // CODING mode: hard guarantee that EVERY element is a coding problem.
        if ("CODING".equals(interviewMode)) {
            sb.append("10. CRITICAL (CODING MODE): Every single element in the JSON array MUST be a coding problem. ")
              .append("You are InterviewIQ AI, a senior technical interviewer. Generate ONLY coding interview problems. ")
              .append("DO NOT generate MCQ questions, behavioral questions, HR questions, or resume questions. ")
              .append("Each problem MUST contain: a problem title, difficulty, a description, constraints, ")
              .append("an input format, an output format, examples, hidden test cases, and a starter method signature. ")
              .append("Every element MUST have \"category\": \"coding\" and \"isCodeQuestion\": true, plus ")
              .append("title, problemDescription, exampleInput, exampleOutput, constraints, starterCode, and testCases. ")
              .append("Return only coding challenges. ")
              .append("DO NOT return any behavioral, HR, resume, project, or generic technical (non-coding) questions.\n");
        }
        sb.append("Generate exactly ").append(count).append(" questions now:");
        return generateContent(sb.toString());
    }

    @Override
    public String generateIntroQuestion(String role, String structuredProfile) {
        String prompt = "You are an interview coach. Write ONE short, friendly, role-aware opening question for a "
                + (role != null ? role : "Software") + " candidate. Base it on this profile JSON: "
                + (structuredProfile != null ? structuredProfile : "{}")
                + ". Reference actual skills/projects. Return ONLY the question text, no quotes, no numbering, no markdown.";
        return generateContent(prompt);
    }

    @Override
    public String generateFollowUp(String question, String answer, String role, String difficulty, String resumeContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are InterviewIQ AI, a real, adaptive technical interviewer continuing a LIVE conversation.\n\n");
        sb.append("ORIGINAL QUESTION: ").append(question).append("\n\n");
        sb.append("CANDIDATE'S ANSWER: ").append(answer).append("\n\n");
        if (role != null) sb.append("TARGET ROLE: ").append(role).append("\n\n");
        if (resumeContext != null && !resumeContext.isBlank()) sb.append("RESUME CONTEXT:\n").append(resumeContext).append("\n\n");
        sb.append("TARGET DIFFICULTY: ").append(difficulty).append("\n\n");
        sb.append("INSTRUCTIONS:\n");
        sb.append("1. Analyse the answer; name a strength and a gap.\n");
        sb.append("2. Generate 1-2 short follow-ups that probe DEEPER, reference the answer, under 200 chars.\n");
        sb.append("3. Return ONLY a raw JSON array; each element: {\"question\": \"<text>\", \"rationale\": \"<why>\"}.\n");
        return generateContent(sb.toString());
    }

    @Override
    public String validateAnswer(String answer, String questionType) {
        String prompt = "You are an AI validating an interview answer. Determine if the text is a genuine attempt to "
                + "answer (even if incorrect/brief) or meaningless spam (keyboard mashing, unrelated nonsense). "
                + "Question Type: " + questionType + "\nAnswer: \"" + answer + "\"\nRespond ONLY with the word VALID or INVALID.";
        return generateContent(prompt);
    }

    @Override
    public String evaluateAnswer(String question, String answer, String requestJson, String judge0Result) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are InterviewIQ AI, an expert technical interviewer evaluating a candidate's answer.\n\n");
        sb.append("QUESTION:\n").append(question).append("\n\n");
        
        if (judge0Result != null && !judge0Result.isBlank()) {
            sb.append("The candidate submitted code which was automatically executed.\n");
            sb.append("Execution Result: ").append(judge0Result).append("\n\n");
            sb.append("CANDIDATE CODE:\n").append(answer).append("\n\n");
        } else {
            sb.append("CANDIDATE ANSWER:\n").append(answer).append("\n\n");
        }

        sb.append("Even if the answer is poor, you MUST return a valid JSON object with low scores (1-10) and constructive content.\n");
        sb.append("Return ONLY raw JSON (no markdown):\n");
        sb.append("{\"score\": <0-100>, \"technicalAccuracy\": <0-100>, \"communication\": <0-100>, ")
          .append("\"problemSolving\": <0-100>, \"codeQuality\": <0-100>, \"projectScore\": <0-100>, ")
          .append("\"confidenceScore\": <0-100>, \"isCorrect\": <true|false>, ")
          .append("\"feedback\": \"<eval>\", \"improvementSuggestions\": \"<tips>\", ")
          .append("\"answerComparison\": \"<compare>\", \"strengths\": [\"<s1>\"], ")
          .append("\"weaknesses\": [\"<w1>\"], \"recommendations\": [\"<r1>\"]}");
        return generateContent(sb.toString());
    }

    @Override
    public String generateFeedback(String interviewType, String qaContext) {
        String prompt = "You are a career coach writing a final feedback report for a " + interviewType + " mock interview.\n\n"
                + "Transcript of questions and the candidate's answers:\n" + qaContext + "\n\n"
                + "Provide a holistic evaluation. Return ONLY raw JSON (no markdown):\n"
                + "{\"overallScore\": <0-100>,\n"
                + " \"categoryScores\": {\n"
                + "   \"communicationScore\": {\"score\": <0-100>, \"comment\": \"<c>\"},\n"
                + "   \"technicalScore\": {\"score\": <0-100>, \"comment\": \"<c>\"},\n"
                + "   \"problemSolvingScore\": {\"score\": <0-100>, \"comment\": \"<c>\"},\n"
                + "   \"codingScore\": {\"score\": <0-100>, \"comment\": \"<c>\"},\n"
                + "   \"confidenceScore\": {\"score\": <0-100>, \"comment\": \"<c>\"}\n"
                + " },\n"
                + " \"strengths\": [\"<s1>\",\"<s2>\"], \"areasOfImprovement\": [\"<i1>\",\"<i2>\"],\n"
                + " \"missedConcepts\": [\"<m1>\",\"<m2>\"], \"recommendedLearningTopics\": [\"<t1>\",\"<t2>\"],\n"
                + " \"weakConcepts\": [\"<wc1>\"], \"strongConcepts\": [\"<sc1>\"],\n"
                + " \"codingPerformance\": \"<assessment of coding skills, or N/A>\",\n"
                + " \"careerGuidance\": \"<tips for next career steps>\",\n"
                + " \"interviewSummary\": \"<3-4 sentence holistic assessment>\",\n"
                + " \"hiringRecommendation\": \"<Hire / No Hire / Borderline>\",\n"
                + " \"category\": \"<Excellent|Good|Average|Needs Improvement>\",\n"
                + " \"evaluated\": true}";
        return generateContent(prompt);
    }

    @Override
    public String analyzeResume(String resumeText) {
        String prompt = "You are an expert technical recruiter. Analyse the resume and extract a structured profile. "
                + "Return ONLY a valid JSON object with these exact keys, each an array of concise strings: "
                + "\"skills\", \"technologies\", \"frameworks\", \"languages\", \"projects\", \"education\", "
                + "\"experience\", \"certifications\", \"achievements\", \"domainsOfExpertise\". "
                + "Do not include other keys, markdown, or prose.\n\nResume Text:\n" + resumeText;
        return generateContent(prompt);
    }

    @Override
    public String generate(String prompt) {
        return generateContent(prompt);
    }
}
