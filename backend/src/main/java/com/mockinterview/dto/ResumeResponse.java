package com.mockinterview.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ResumeResponse {
    private Long id;
    private String fileName;
    private String extractedText;
    private String structuredSkills;

    // Dedicated structured-profile fields (mirror the resume table columns).
    private String skills;
    private String technologies;
    private String frameworks;
    private String languages;
    private String projects;
    private String education;
    private String experience;
    private String certifications;
    private String achievements;
    private String domainsOfExpertise;

    private LocalDateTime uploadedAt;
}
