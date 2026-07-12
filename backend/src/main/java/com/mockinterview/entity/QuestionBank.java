package com.mockinterview.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "question_bank")
@Data
public class QuestionBank {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String role;
    private String difficulty;
    private String category;
    private String questionText;
    private String expectedAnswer;
    private Boolean isActive;
    
    private LocalDateTime createdAt;
}
