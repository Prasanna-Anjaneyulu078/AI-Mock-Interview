package com.mockinterview.service;

import com.mockinterview.entity.Interview;
import com.mockinterview.entity.Question;
import com.mockinterview.entity.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class LocalCodingQuestionProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalCodingQuestionProvider.class);

    private final com.mockinterview.repository.CodingQuestionBankRepository codingQuestionBankRepository;

    public LocalCodingQuestionProvider(com.mockinterview.repository.CodingQuestionBankRepository codingQuestionBankRepository) {
        this.codingQuestionBankRepository = codingQuestionBankRepository;
    }

    public Question buildQuestion(Interview interview, String difficulty, String language, Set<String> seen) {
        if (seen == null) seen = new java.util.HashSet<>();
        
        String role = interview.getInterviewType() != null ? interview.getInterviewType().toLowerCase() : "cs_fundamentals";
        
        List<com.mockinterview.entity.CodingQuestionBank> dbQuestions = codingQuestionBankRepository.findRandomCodingQuestions(role, difficulty, 10);
        
        com.mockinterview.entity.CodingQuestionBank chosen = null;
        for (com.mockinterview.entity.CodingQuestionBank qb : dbQuestions) {
            if (!seen.contains(qb.getTitle())) {
                chosen = qb;
                break;
            }
        }
        
        if (chosen == null) {
            log.warn("[FALLBACK_CODING_PROVIDER_USED] pool exhausted for interview {}", interview.getId());
            return null;
        }
        
        log.info("[FALLBACK_CODING_PROVIDER_USED] true title='{}'", chosen.getTitle());
        
        Question q = new Question();
        q.setInterview(interview);
        q.setQuestionText(chosen.getDescription());
        q.setTitle(chosen.getTitle());
        q.setType("coding");
        q.setDifficulty(difficulty);
        q.setIsCodeQuestion(true);
        q.setCodeType("write");
        q.setCodeSnippet(chosen.getStarterCode());
        q.setCodeLanguage(language != null && !language.isBlank() ? language.toLowerCase() : "javascript");
        q.setSolutionCode(chosen.getSolution());
        q.setExpectedAnswer("Time: " + chosen.getExpectedTimeComplexity() + ", Space: " + chosen.getExpectedSpaceComplexity());
        q.setExplanation("Time: " + chosen.getExpectedTimeComplexity() + ", Space: " + chosen.getExpectedSpaceComplexity());
        q.setConstraints(chosen.getConstraints());
        q.setGeneratedByAI(false);
        
        // Mock test cases for now, as it's a fallback.
        List<TestCase> tcs = new java.util.ArrayList<>();
        if (chosen.getTestCases() != null && !chosen.getTestCases().isBlank()) {
            TestCase t1 = new TestCase();
            t1.setQuestion(q);
            t1.setInput("Mock Input");
            t1.setExpectedOutput(chosen.getTestCases());
            t1.setHidden(false);
            tcs.add(t1);
        }
        q.setTestCases(tcs);
        
        seen.add(chosen.getTitle());
        return q;
    }
}
