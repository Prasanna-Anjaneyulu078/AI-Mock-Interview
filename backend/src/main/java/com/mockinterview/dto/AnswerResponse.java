package com.mockinterview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AnswerResponse {
    // FIX: Lombok generates isIsComplete() for a boolean field named "isComplete",
    // which Jackson serializes as "complete". Renaming to "complete" with an explicit
    // @JsonProperty("isComplete") ensures the frontend receives "isComplete": true/false.
    @JsonProperty("isComplete")
    private boolean complete;

    private String response;
    private Integer currentQuestion;
    private Integer totalQuestions;
    private QuestionDTO question;
    private String audio;
    private String message; // For farewell message when complete
    private Object evaluation; // For code evaluation results

    /** Follow-up questions generated for the just-answered question (spec #4). */
    private List<QuestionDTO> followUps;
}
