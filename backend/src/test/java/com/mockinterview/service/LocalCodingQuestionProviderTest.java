package com.mockinterview.service;

import com.mockinterview.entity.CodingQuestionBank;
import com.mockinterview.entity.Interview;
import com.mockinterview.entity.Question;
import com.mockinterview.repository.CodingQuestionBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies the mapping and fallback logic of LocalCodingQuestionProvider.
 */
class LocalCodingQuestionProviderTest {

    private CodingQuestionBankRepository repository;
    private LocalCodingQuestionProvider provider;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(CodingQuestionBankRepository.class);
        provider = new LocalCodingQuestionProvider(repository);
    }

    @Test
    void devopsRoleSelectsDevOpsProblems() {
        Interview interview = new Interview();
        interview.setInterviewType("DEVOPS_ENGINEER");

        CodingQuestionBank qb = new CodingQuestionBank();
        qb.setTitle("Parse Apache Access Logs");
        qb.setDescription("Description");

        when(repository.findRandomCodingQuestions(eq("devops_engineer"), eq("medium"), anyInt()))
                .thenReturn(List.of(qb));

        Set<String> seen = new HashSet<>();
        Question q = provider.buildQuestion(interview, "medium", "python", seen);
        
        assertNotNull(q);
        String title = q.getTitle();
        
        assertTrue(title.toLowerCase().contains("log")
                        || title.toLowerCase().contains("cron")
                        || title.toLowerCase().contains("docker")
                        || title.toLowerCase().contains("kubernetes"),
                "Expected a DevOps-flavoured problem but got: " + title);
    }

    @Test
    void dataAnalystRoleSelectsDataAnalystProblems() {
        Interview interview = new Interview();
        interview.setInterviewType("DATA_ANALYST");

        CodingQuestionBank qb = new CodingQuestionBank();
        qb.setTitle("Clean Customer Data");
        
        when(repository.findRandomCodingQuestions(eq("data_analyst"), eq("medium"), anyInt()))
                .thenReturn(List.of(qb));

        Set<String> seen = new HashSet<>();
        Question q = provider.buildQuestion(interview, "medium", "python", seen);
        assertNotNull(q);
        String title = q.getTitle();
        
        assertTrue(title.toLowerCase().contains("customer")
                        || title.toLowerCase().contains("sales")
                        || title.toLowerCase().contains("average")
                        || title.toLowerCase().contains("clean"),
                "Expected a Data-Analyst-flavoured problem but got: " + title);
    }

    @Test
    void genericRoleUsesAlgorithmPool() {
        Interview interview = new Interview();
        interview.setInterviewType(null); // Defaults to cs_fundamentals

        CodingQuestionBank qb = new CodingQuestionBank();
        qb.setTitle("Two Sum");
        
        when(repository.findRandomCodingQuestions(eq("cs_fundamentals"), eq("medium"), anyInt()))
                .thenReturn(List.of(qb));

        Set<String> seen = new HashSet<>();
        Question q = provider.buildQuestion(interview, "medium", "python", seen);
        assertNotNull(q);
        assertEquals("Two Sum", q.getTitle());
    }

    @Test
    void testAvoidsSeenQuestions() {
        Interview interview = new Interview();
        interview.setInterviewType("cs_fundamentals");

        CodingQuestionBank qb1 = new CodingQuestionBank();
        qb1.setTitle("Two Sum");
        
        CodingQuestionBank qb2 = new CodingQuestionBank();
        qb2.setTitle("Three Sum");

        when(repository.findRandomCodingQuestions(eq("cs_fundamentals"), eq("medium"), anyInt()))
                .thenReturn(List.of(qb1, qb2));

        Set<String> seen = new HashSet<>();
        seen.add("Two Sum");
        
        Question q = provider.buildQuestion(interview, "medium", "python", seen);
        assertNotNull(q);
        assertEquals("Three Sum", q.getTitle());
        assertTrue(seen.contains("Three Sum"));
    }
}
