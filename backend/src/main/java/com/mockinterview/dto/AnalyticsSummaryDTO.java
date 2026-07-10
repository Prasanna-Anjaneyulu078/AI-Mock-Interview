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
    private double codingAccuracy; // percentage of test cases passed
    private double passRate; // percentage of Hire / Borderline out of all evaluated

    private java.util.List<RadarPoint> radarData;
    private java.util.List<BarPoint> languageData;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RadarPoint {
        private String subject;
        private double score;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BarPoint {
        private String language;
        private int count;
    }
}
