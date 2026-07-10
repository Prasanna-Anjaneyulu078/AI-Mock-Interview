package com.mockinterview.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Base for cloud LLM providers. Centralises prompt construction (so every provider emits
 * the exact JSON field names the service layer parses) and delegates the actual HTTP call
 * to generateContent. The local engine (LocalRuleProvider) implements the interface directly.
 */
public abstract class AbstractLLMProvider implements AIProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected abstract String generateContent(String prompt);

    private static final List<Map.Entry<String, String>> ROLE_FOCUS = List.of(
            Map.entry("java", "Core Java, OOP, Collections, Concurrency, Spring Boot, Microservices, Security."),
            Map.entry("react", "React.js, Hooks, Component design, State Management, Performance, Accessibility."),
            Map.entry("full stack", "Frontend, Backend APIs, Database Design, REST/GraphQL, Auth."),
            Map.entry("frontend", "HTML/CSS, JS/TS, Frameworks, State Management, Web Perf, a11y."),
            Map.entry("backend", "Server logic, REST API design, Databases/ORM, Auth, Caching, Queues."),
            Map.entry("python", "Python, Stdlib, Django/Flask/FastAPI, Scripting, Data structures."),
            Map.entry("data scientist", "Statistics, ML, Data Analysis, Feature Engineering, Model Eval."),
            Map.entry("data", "SQL, Python/R, Visualization, Statistics, Analytics."),
            Map.entry("devops", "CI/CD, Docker & Kubernetes, Cloud, IaC, Monitoring."),
            Map.entry("ai", "ML, Deep Learning, LLMs, RAG, Vector DBs, Deployment."),
            Map.entry("ml", "ML, Deep Learning, LLMs, RAG, Vector DBs, Deployment."),
            Map.entry("machine learning", "ML, Deep Learning, LLMs, RAG, Vector DBs, Deployment.")
    );

    private static final Map<String, String> LEVEL_STYLE = Map.of(
            "Easy", "Style: short, direct recall/understanding questions.",
            "Medium", "Style: scenario and applied questions.",
            "Hard", "Style: open-ended design and trade-off discussions."
    );

    private static String resolveRoleFocus(String role) {
        if (role == null) return null;
        String r = role.toLowerCase();
        for (var e : ROLE_FOCUS) {
            if (r.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    @Override
    public String generateQuestions(String role, String resumeContext, String guidance, String levelDifficulty,
                                    int hr, int tech, int proj, int count, String avoidList) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert technical interviewer for a ")
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
        sb.append("RULES:\n");
        sb.append("1. Return ONLY a raw JSON array. No markdown, no prose.\n");
        sb.append("2. Generate exactly ").append(hr).append(" behavioral/HR questions (category: \"behavioral\").\n");
        sb.append("3. Generate exactly ").append(tech).append(" technical questions (category: \"technical\").\n");
        sb.append("4. Generate exactly ").append(proj).append(" project/experience questions (category: \"project\").\n");
        sb.append("5. Each element MUST have: \"question\", \"category\", \"difficulty\" (exactly \"")
          .append(levelDifficulty).append("\"), \"isCodeQuestion\" (boolean), \"idealAnswer\", \"explanation\".\n");
        sb.append("6. Reference the candidate's actual skills/projects. For 1-2 technical/project questions include ")
          .append("\"isCodeQuestion\": true and a \"testCases\" array (up to 3 cases with input/expectedOutput/hidden).\n");
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
        sb.append("You are a real, adaptive technical interviewer continuing a LIVE conversation.\n\n");
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
        sb.append("You are an expert technical interviewer evaluating a candidate's answer.\n\n");
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
                + "   \"communicationSkills\": {\"score\": <0-100>, \"comment\": \"<c>\"},\n"
                + "   \"technicalKnowledge\": {\"score\": <0-100>, \"comment\": \"<c>\"},\n"
                + "   \"problemSolving\": {\"score\": <0-100>, \"comment\": \"<c>\"},\n"
                + "   \"codeQuality\": {\"score\": <0-100>, \"comment\": \"<c>\"},\n"
                + "   \"confidence\": {\"score\": <0-100>, \"comment\": \"<c>\"}},\n"
                + " \"strengths\": [\"<s1>\",\"<s2>\"], \"areasOfImprovement\": [\"<i1>\",\"<i2>\"],\n"
                + " \"recommendedTopics\": [\"<t1>\",\"<t2>\"], \"weakConcepts\": [\"<wc1>\"], \"strongConcepts\": [\"<sc1>\"],\n"
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
