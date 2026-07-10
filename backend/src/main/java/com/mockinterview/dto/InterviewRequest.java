package com.mockinterview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InterviewRequest {
    @NotBlank(message = "Role is required")
    private String role;

    private Long resumeId;
    private String interviewLevel; // STARTER, STANDARD, ADVANCED
    
    private Boolean voiceEnabled;
    private String voiceName;
    private Double voiceSpeed;

    // Murf voice selection (populated by the setup UI voice picker)
    private String voiceId;
    private String style;
}
