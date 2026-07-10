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
