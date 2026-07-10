package com.mockinterview.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;


@Data
public class InterviewResponse {
    private String interviewId; // Client expects a string id
    private String greeting;
    private Integer currentQuestion;
    private Integer totalQuestions;
    private Integer targetQuestions;
    private QuestionDTO question;
    private String audio; // base64 encoded audio or URL

    private List<QuestionDTO> questions; // For getting full interview
    private List<MessageDTO> messages;   // Chat messages (interviewer + user)

    private String status;
    private Double overallScore;
    private String interviewType;
    private String role;       // alias for interviewType — FeedbackPage reads `role`
    private String adaptedDifficulty; // #5 adaptive engine: difficulty tuned mid-interview

    // Feedback as parsed JSON object (FeedbackPage reads interview.feedback.categoryScores etc.)
    private Object feedback;

    private LocalDateTime createdAt; // FeedbackPage formats this date
    private String lastAudio;        // InterviewPage uses location.state?.audio || data.lastAudio
}

