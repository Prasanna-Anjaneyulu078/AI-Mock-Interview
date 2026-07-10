package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "coding_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodingSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Interview interview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coding_question_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CodingQuestion codingQuestion;

    @Column(columnDefinition = "TEXT")
    private String sourceCode;

    private String language;

    private Double executionTime; // seconds
    private Double memoryUsage; // KB

    private String status; // Accepted, Wrong Answer, TLE, MLE, Compile Error, Runtime Error

    @CreationTimestamp
    private LocalDateTime submittedAt;
}
