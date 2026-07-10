package com.mockinterview.dto;

import lombok.Data;

@Data
public class QuestionDTO {
    private Long id;
    private String text; // Map from questionText
    private String type;
    private Boolean isCodeQuestion;
    private String codeType;
    private String codeSnippet;
    private String codeLanguage;

    // ─── Coding Problem Detail Fields (Issue #2: LeetCode-style display) ───
    /** Short display title, e.g. "Two Sum". Falls back to text if blank. */
    private String title;

    /** Full problem statement. */
    private String problemDescription;

    /** Example input string (may be multi-line). */
    private String exampleInput;

    /** Expected output for the example. */
    private String exampleOutput;

    /** Constraints on input size, values, etc. */
    private String constraints;

    /** Starter code template. */
    private String starterCode;

    /** Comma-separated topic tags. */
    private String tags;

    /** Expected time complexity hint, e.g. "O(n)". */
    private String timeComplexity;
}
