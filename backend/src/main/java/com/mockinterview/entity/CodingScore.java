package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coding_scores")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodingScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CodingSubmission submission;

    private Double testCaseScore; // 40%
    private Double codeQualityScore; // 20%
    private Double timeComplexityScore; // 15%
    private Double spaceComplexityScore; // 15%
    private Double codingStyleScore; // 10%

    private Double totalScore;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String weaknesses;

    @Column(columnDefinition = "TEXT")
    private String optimizationSuggestions;
}
