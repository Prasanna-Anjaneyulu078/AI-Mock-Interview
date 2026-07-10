package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_histories", indexes = {
        @Index(name = "idx_history_user", columnList = "user_id"),
        @Index(name = "idx_history_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    private Double totalScore;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    // Skill-level insights (#8) persisted per interview for analytics.
    @Column(columnDefinition = "TEXT")
    private String strongSkills; // JSON array string

    @Column(columnDefinition = "TEXT")
    private String weakSkills;   // JSON array string

    @CreationTimestamp
    private LocalDateTime createdAt;
}
