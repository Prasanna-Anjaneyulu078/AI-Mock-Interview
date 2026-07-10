package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "answers", indexes = @Index(name = "idx_answer_question", columnList = "question_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(columnDefinition = "TEXT")
    private String answerText;

    @Column(columnDefinition = "TEXT")
    private String transcriptionText;

    private Double evaluationScore;

    // ── Advanced structured evaluation (#7) ──
    private Double technicalScore;
    private Double communicationScore;
    private Double problemSolvingScore;
    private Double codeQualityScore;
    private Double projectScore;
    private Double confidenceScore;

    @Column(columnDefinition = "TEXT")
    private String strengths;      // JSON array string

    @Column(columnDefinition = "TEXT")
    private String weaknesses;     // JSON array string

    @Column(columnDefinition = "TEXT")
    private String recommendations; // JSON array string

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(columnDefinition = "TEXT")
    private String improvementSuggestions;

    @Column(columnDefinition = "TEXT")
    private String answerComparison;

    private String codeLanguage; // if code submission

    @Column(columnDefinition = "TEXT")
    private String codeExecutionResult; // Judge0 summary (passedTests/totalTests) for code answers

    @CreationTimestamp
    private LocalDateTime answeredAt;
}
