package com.mockinterview.dto;

import lombok.Data;

@Data
public class FeedbackResponse {
    private String interviewId;
    private Object feedback; // Can map to a Map or strongly typed class later depending on Gemini response structure
    private Double overallScore;
    /**
     * Whether a real AI evaluation produced this report. {@code false} means the scores are
     * NEUTRAL_BASELINE placeholders (the AI service was unavailable), not a genuine grade.
     */
    private Boolean evaluated;
    private java.util.List<QuestionFeedbackDTO> questions;
}
