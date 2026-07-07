package com.mockinterview.dto;

import lombok.Data;
import java.util.List;

@Data
public class InterviewResponse {
    private String interviewId; // Client expects a string id, usually mongo _id. We will map Long to String.
    private String greeting;
    private Integer currentQuestion;
    private Integer totalQuestions;
    private QuestionDTO question;
    private String audio; // base64 encoded audio or URL
    
    private List<QuestionDTO> questions; // For getting full interview
    private String status;
    private Double overallScore;
    private String interviewType;
}
