package com.mockinterview.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InterviewRequest {
    @NotBlank(message = "Role is required")
    private String role;
    
    private String resumeText;
    private String candidateName;
    private Integer totalQuestions = 5;
}
