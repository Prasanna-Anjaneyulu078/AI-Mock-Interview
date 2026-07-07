package com.mockinterview.dto;

import lombok.Data;

@Data
public class AnswerResponse {
    private boolean isComplete;
    private String response;
    private Integer currentQuestion;
    private Integer totalQuestions;
    private QuestionDTO question;
    private String audio;
    private String message; // For farewell message when complete
    private Object evaluation; // For code evaluation results
}
