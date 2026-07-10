package com.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionFeedbackDTO {
    private String questionText;
    private String type;
    private String difficulty;
    private String candidateAnswer;
    private String idealAnswer;
    private String explanation;
    private Double score;
    private String feedback;
    private String improvementSuggestions;
    private String answerComparison;

    // Advanced structured evaluation (#7)
    private Double technicalScore;
    private Double communicationScore;
    private Double problemSolvingScore;
    private Double codeQualityScore;
    private Double projectScore;
    private Double confidenceScore;

    private String strengths;
    private String weaknesses;
    private String recommendations;

    // Coding Result details
    private String codeLanguage;
    private String codeSnippet;
    private String compileOutput;
    private Integer passedTests;
    private Integer totalTests;
    private String executionStatus;
    
    // Voice/Audio Feedback
    private String recordingUrl;
    private Double speakingSpeed;
    private Double fluencyScore;
    private Integer fillerWordsCount;
}
