package com.mockinterview.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TextSimilarityTest {

    @Test
    void identicalTextsHaveJaccardOne() {
        assertEquals(1.0, TextSimilarity.jaccard("Explain Spring Boot", "Explain Spring Boot"));
    }

    @Test
    void disjointTextsHaveJaccardZero() {
        assertEquals(0.0, TextSimilarity.jaccard("Explain Spring Boot", "Cook a pizza"), 1e-9);
    }

    @Test
    void rephrasedQuestionIsFlaggedDuplicate() {
        Set<String> seen = Set.of("What is Spring Boot?");
        assertTrue(TextSimilarity.isDuplicate("Can you explain Spring Boot?", seen, 0.7));
    }

    @Test
    void distinctResumeQuestionsAreNotDuplicate() {
        Set<String> seen = Set.of("How did you implement JWT auth in your Placement Management System?");
        assertFalse(TextSimilarity.isDuplicate(
                "How does your AI Disease Detection model handle class imbalance?", seen, 0.7));
    }

    @Test
    void normalizeLowercasesAndTrims() {
        assertEquals("java developer", TextSimilarity.normalize("  Java Developer "));
    }
}
