package com.mockinterview.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ResumeResponse {
    private Long id;
    private String fileName;
    private String extractedText; // match original API
    private LocalDateTime uploadedAt;
}
