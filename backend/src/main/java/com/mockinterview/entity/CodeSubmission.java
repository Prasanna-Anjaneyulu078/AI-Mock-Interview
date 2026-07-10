package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "code_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String code;

    private String language;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    // ── Judge0 execution metrics (spec #10) ──
    @Column(columnDefinition = "TEXT")
    private String stdout;

    @Column(columnDefinition = "TEXT")
    private String stderr;

    private Double executionTime;  // seconds
    private Double memoryUsage;    // KB

    @Column(name = "passed")
    private Boolean passed;

    private Integer passedTests;
    private Integer totalTests;

    @Column(columnDefinition = "TEXT")
    private String status;

    @Column(columnDefinition = "TEXT")
    private String compileOutput;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
