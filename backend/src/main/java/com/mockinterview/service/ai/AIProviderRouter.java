package com.mockinterview.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
@Primary
public class AIProviderRouter implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(AIProviderRouter.class);

    private final OpenRouterProvider openRouter;
    private final GroqProvider groq;
    private final OllamaProvider ollama;
    private final LocalRuleProvider localRule;

    private String activeProvider = "None";

    public AIProviderRouter(OpenRouterProvider openRouter,
                            GroqProvider groq,
                            OllamaProvider ollama,
                            LocalRuleProvider localRule) {
        this.openRouter = openRouter;
        this.groq = groq;
        this.ollama = ollama;
        this.localRule = localRule;
    }

    private String executeWithFailover(String operationName, Supplier<String> openRouterAction, Supplier<String> groqAction, Supplier<String> ollamaAction, Supplier<String> localAction) {
        if (openRouter.isHealthy()) {
            try {
                String result = openRouterAction.get();
                activeProvider = "OpenRouter";
                return result;
            } catch (Exception e) {
                log.warn("OpenRouter failed for {}: {}", operationName, e.getMessage());
            }
        }

        if (groq.isHealthy()) {
            try {
                String result = groqAction.get();
                activeProvider = "Groq";
                return result;
            } catch (Exception e) {
                log.warn("Groq failed for {}: {}", operationName, e.getMessage());
            }
        }

        if (ollama.isHealthy()) {
            try {
                String result = ollamaAction.get();
                activeProvider = "Ollama";
                return result;
            } catch (Exception e) {
                log.warn("Ollama failed for {}: {}", operationName, e.getMessage());
            }
        }

        log.warn("All AI providers failed for {}. Using LocalRuleProvider.", operationName);
        activeProvider = "LocalRuleEngine";
        return localAction.get();
    }

    @Override
    public String generateQuestions(String role, String resumeContext, String guidance, String levelDifficulty, int hr, int tech, int proj, int codeCount, int interestCount, String selectedInterests, int count, String avoidList) {
        return executeWithFailover("generateQuestions",
                () -> openRouter.generateQuestions(role, resumeContext, guidance, levelDifficulty, hr, tech, proj, codeCount, interestCount, selectedInterests, count, avoidList),
                () -> groq.generateQuestions(role, resumeContext, guidance, levelDifficulty, hr, tech, proj, codeCount, interestCount, selectedInterests, count, avoidList),
                () -> ollama.generateQuestions(role, resumeContext, guidance, levelDifficulty, hr, tech, proj, codeCount, interestCount, selectedInterests, count, avoidList),
                () -> localRule.generateQuestions(role, resumeContext, guidance, levelDifficulty, hr, tech, proj, codeCount, interestCount, selectedInterests, count, avoidList));
    }

    @Override
    public String generateIntroQuestion(String role, String structuredProfile) {
        return executeWithFailover("generateIntroQuestion",
                () -> openRouter.generateIntroQuestion(role, structuredProfile),
                () -> groq.generateIntroQuestion(role, structuredProfile),
                () -> ollama.generateIntroQuestion(role, structuredProfile),
                () -> localRule.generateIntroQuestion(role, structuredProfile));
    }

    @Override
    public String generateFollowUp(String question, String answer, String role, String difficulty, String resumeContext) {
        return executeWithFailover("generateFollowUp",
                () -> openRouter.generateFollowUp(question, answer, role, difficulty, resumeContext),
                () -> groq.generateFollowUp(question, answer, role, difficulty, resumeContext),
                () -> ollama.generateFollowUp(question, answer, role, difficulty, resumeContext),
                () -> localRule.generateFollowUp(question, answer, role, difficulty, resumeContext));
    }

    @Override
    public String validateAnswer(String answer, String questionType) {
        return executeWithFailover("validateAnswer",
                () -> openRouter.validateAnswer(answer, questionType),
                () -> groq.validateAnswer(answer, questionType),
                () -> ollama.validateAnswer(answer, questionType),
                () -> localRule.validateAnswer(answer, questionType));
    }

    @Override
    public String evaluateAnswer(String question, String answer, String requestJson, String judge0Result) {
        return executeWithFailover("evaluateAnswer",
                () -> openRouter.evaluateAnswer(question, answer, requestJson, judge0Result),
                () -> groq.evaluateAnswer(question, answer, requestJson, judge0Result),
                () -> ollama.evaluateAnswer(question, answer, requestJson, judge0Result),
                () -> localRule.evaluateAnswer(question, answer, requestJson, judge0Result));
    }

    @Override
    public String generateFeedback(String interviewType, String qaContext) {
        return executeWithFailover("generateFeedback",
                () -> openRouter.generateFeedback(interviewType, qaContext),
                () -> groq.generateFeedback(interviewType, qaContext),
                () -> ollama.generateFeedback(interviewType, qaContext),
                () -> localRule.generateFeedback(interviewType, qaContext));
    }

    @Override
    public String analyzeResume(String resumeText) {
        return executeWithFailover("analyzeResume",
                () -> openRouter.analyzeResume(resumeText),
                () -> groq.analyzeResume(resumeText),
                () -> ollama.analyzeResume(resumeText),
                () -> localRule.analyzeResume(resumeText));
    }

    @Override
    public String generate(String prompt) {
        return executeWithFailover("generate",
                () -> openRouter.generate(prompt),
                () -> groq.generate(prompt),
                () -> ollama.generate(prompt),
                () -> localRule.generate(prompt));
    }
    @Override
    public String getProviderName() {
        return "Router (Active: " + activeProvider + ")";
    }
    
    public String getActiveProvider() {
        return activeProvider;
    }

    @Override
    public boolean isHealthy() {
        return openRouter.isHealthy() || groq.isHealthy() || ollama.isHealthy();
    }
}




