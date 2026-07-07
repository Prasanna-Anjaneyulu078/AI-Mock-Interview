package com.mockinterview.dto;

import lombok.Data;

@Data
public class FeedbackResponse {
    private String interviewId;
    private Object feedback; // Can map to a Map or strongly typed class later depending on Gemini response structure
    private Double overallScore;
}
