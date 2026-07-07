package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions")
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
    private String expectedAnswer;

    private Boolean generatedByAI;

    private String type; // e.g. "behavioral", "technical"
    
    private Boolean isCodeQuestion;

    private String codeType; // e.g. "write", "fix", "explain"

    @Column(columnDefinition = "TEXT")
    private String codeSnippet; // starter/buggy code for code questions

    private String codeLanguage; // e.g. "javascript", "python"

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<Answer> answers = new ArrayList<>();
}
