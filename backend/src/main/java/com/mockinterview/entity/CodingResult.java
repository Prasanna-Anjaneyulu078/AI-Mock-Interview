package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coding_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @com.fasterxml.jackson.annotation.JsonIgnore
    private CodingSubmission submission;

    private Integer passedTests;
    private Integer failedTests;
    private Integer totalTests;

    @Column(columnDefinition = "TEXT")
    private String compileOutput;

    @Column(columnDefinition = "TEXT")
    private String stdout;

    @Column(columnDefinition = "TEXT")
    private String stderr;

    private Boolean timeLimitExceeded;
    private Boolean memoryLimitExceeded;
    private Boolean compilationError;
    private Boolean runtimeError;

    // AI Evaluation fields
    private Double codeQualityScore;
    private Double timeComplexityScore;
    private Double spaceComplexityScore;
    private Double styleScore;
    private Double finalScore;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(columnDefinition = "TEXT")
    private String optimizationSuggestions;
}
