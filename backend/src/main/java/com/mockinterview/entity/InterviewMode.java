package com.mockinterview.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "interview_modes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewMode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String modeName; // HR, TECHNICAL, RESUME, PROJECT, MCQ, CODING_INTERVIEW, INTEREST_BASED, HYBRID
}
