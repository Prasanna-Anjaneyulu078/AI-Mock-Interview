package com.mockinterview.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "coding_question_bank")
@Data
public class CodingQuestionBank {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String title;
    private String description;
    private String difficulty;
    private String role;
    private String language;
    private String constraints;
    private String starterCode;
    private String solution;
    private String testCases;
    private String hiddenTestCases;
    private String expectedTimeComplexity;
    private String expectedSpaceComplexity;
    
    private LocalDateTime createdAt;
}
