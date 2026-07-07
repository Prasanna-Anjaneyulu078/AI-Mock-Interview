package com.mockinterview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mockinterview.entity.InterviewHistory;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HistoryEntryDTO {

    // Frontend uses `interview._id` so we expose the history entry id as `_id`
    @JsonProperty("_id")
    private Long _id;

    // Interview fields
    private String role;         // maps to interview.interviewType
    private String status;       // maps to interview.status
    private Integer totalQuestions;
    private String difficulty;

    // Score from InterviewHistory (the evaluated overall score)
    private Double overallScore;

    // Timestamp of the history record
    private LocalDateTime createdAt;

    /**
     * Factory method to build a DTO from an InterviewHistory entity.
     * Avoids any lazy-loading or circular serialization issues.
     */
    public static HistoryEntryDTO from(InterviewHistory history) {
        HistoryEntryDTO dto = new HistoryEntryDTO();
        dto.createdAt = history.getCreatedAt();
        dto.overallScore = history.getTotalScore();

        if (history.getInterview() != null) {
            dto._id = history.getInterview().getId();
            dto.role = history.getInterview().getInterviewType();
            dto.status = history.getInterview().getStatus();
            dto.totalQuestions = history.getInterview().getTotalQuestions();
            dto.difficulty = history.getInterview().getDifficulty();
        } else {
            dto._id = history.getId(); // fallback
        }

        return dto;
    }
}
