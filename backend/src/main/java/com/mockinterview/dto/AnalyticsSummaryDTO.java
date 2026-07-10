package com.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregated performance summary for the authenticated candidate. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryDTO {
    private int totalInterviews;
    private double averageScore;
    private double bestScore;
    private double completionRate; // percentage 0-100
}
