package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "interviews", indexes = @Index(name = "idx_interview_user", columnList = "user_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String interviewType; // e.g. original "role"

    private String difficulty; // not explicitly in old schema but standard

    // Adaptive engine (#5): difficulty tuned mid-interview from running performance.
    private String adaptedDifficulty;

    private Double runningScore; // rolling average of answer evaluation scores

    @Column(nullable = false)
    private String status; // "in_progress", "completed"

    private Double score; // original "overallScore"

    @CreationTimestamp
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    // Derived fields to map old features
    @Column(columnDefinition = "TEXT")
    private String resumeText;

    private Integer totalQuestions;

    private Integer currentQuestion;

    @Column(columnDefinition = "TEXT")
    private String feedback; // Storing as JSON string for now

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<Question> questions = new ArrayList<>();

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<CodeSubmission> codeSubmissions = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String lastAudio;

    private Boolean voiceEnabled;
    private String voiceName;
    private Double voiceSpeed;

    // Selected Murf voice (voiceId) and style for this session
    private String voiceId;
    private String style;

    // Link back to the resume used for this interview (enables same-resume dedup)
    private Long resumeId;
}
