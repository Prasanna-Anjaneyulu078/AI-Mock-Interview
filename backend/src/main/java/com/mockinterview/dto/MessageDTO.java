package com.mockinterview.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Safe DTO for Message entity — avoids lazy-loading/circular-ref issues
 * when serializing Interview messages through Jackson.
 */
@Data
public class MessageDTO {
    private Long id;
    private String role;    // "interviewer" | "user"
    private String content;
    private LocalDateTime createdAt;
}
