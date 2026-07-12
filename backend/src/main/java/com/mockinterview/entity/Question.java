package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions", indexes = @Index(name = "idx_question_interview", columnList = "interview_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @Column(columnDefinition = "TEXT")
    private String expectedAnswer; // mapped to idealAnswer

    @Column(columnDefinition = "TEXT")
    private String explanation;

    private String difficulty;

    /**
     * Stable 1-based position of this question within the interview. Follow-ups are
     * inserted immediately after their parent's sequence so the interview flows
     * naturally (the next request returns the follow-up) and survives page reloads.
     */
    private Integer sequence;

    private Boolean generatedByAI;

    private String type; // e.g. "behavioral", "technical"
    
    private Boolean isCodeQuestion;

    private Boolean isFollowUp; // true for dynamically generated follow-up questions

    private Long parentQuestionId; // the question this follow-up probes (nullable)

    private String codeType; // e.g. "write", "fix", "explain"

    @Column(columnDefinition = "TEXT")
    private String codeSnippet; // starter/buggy code for code questions

    private String codeLanguage; // e.g. "javascript", "python"

    // ─── Coding Problem Detail Fields (Issue #2: LeetCode-style display) ───

    /** Short display title, e.g. "Two Sum". */
    private String title;

    /** Full problem statement / description. */
    @Column(columnDefinition = "TEXT")
    private String problemDescription;

    /** Example input for the problem, plain text or formatted. */
    @Column(columnDefinition = "TEXT")
    private String exampleInput;

    /** Expected output matching the example input. */
    @Column(columnDefinition = "TEXT")
    private String exampleOutput;

    /** Constraints on input size, value ranges, etc. */
    @Column(columnDefinition = "TEXT")
    private String constraints;

    /** Starter code / template (method signature only — NO implementation body). */
    @Column(columnDefinition = "TEXT")
    private String starterCode;

    /** Full reference solution — stored server-side, NEVER sent to frontend. */
    @Column(columnDefinition = "TEXT")
    private String solutionCode;

    /** Comma-separated tags, e.g. "Array, Hash Map, Two Pointers". */
    private String tags;

    /** Expected time complexity hint, e.g. "O(n)". */
    private String timeComplexity;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<Answer> answers = new ArrayList<>();

    /** Auto-grading test cases (visible + hidden) executed by Judge0 for code questions. */
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<TestCase> testCases = new ArrayList<>();
}
