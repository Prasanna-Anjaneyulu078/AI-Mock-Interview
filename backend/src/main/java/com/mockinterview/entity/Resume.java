package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String fileName;

    @Column(columnDefinition = "TEXT")
    private String resumeText; // original "extractedText"

    @Column(columnDefinition = "TEXT")
    private String structuredSkills; // Full structured profile JSON (all 8 fields)

    // ── Dedicated structured profile columns (replaces reliance on the JSON blob) ──
    @Column(columnDefinition = "TEXT")
    private String skills;          // JSON array string, e.g. ["Java","Spring Boot"]

    @Column(columnDefinition = "TEXT")
    private String technologies;    // JSON array string

    @Column(columnDefinition = "TEXT")
    private String frameworks;      // JSON array string

    @Column(columnDefinition = "TEXT")
    private String languages;       // JSON array string (programming languages)

    @Column(columnDefinition = "TEXT")
    private String projects;        // JSON array string

    @Column(columnDefinition = "TEXT")
    private String education;       // JSON array string

    @Column(columnDefinition = "TEXT")
    private String experience;      // JSON array string

    @Column(columnDefinition = "TEXT")
    private String certifications;  // JSON array string

    @Column(columnDefinition = "TEXT")
    private String achievements;    // JSON array string

    @Column(columnDefinition = "TEXT")
    private String domainsOfExpertise; // JSON array string (e.g. "Backend", "Cloud")

    @CreationTimestamp
    private LocalDateTime uploadedAt;
}
