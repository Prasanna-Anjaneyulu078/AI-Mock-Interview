package com.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Time-series + history data powering the dashboard's trend charts. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsProgressDTO {

    /** Score progression, oldest → newest. */
    private List<TrendPoint> performanceTrend;

    /** Cumulative distinct strong/weak skill counts over time. */
    private List<SkillGrowthPoint> skillGrowthTrend;

    /** Difficulty progression over time. */
    private List<TrendPoint> difficultyProgression;

    /** Monthly performance trends. */
    private List<TrendPoint> monthlyTrends;

    /** Per-interview summary rows. */
    private List<HistorySummary> history;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        private String label; // interview date (yyyy-MM-dd)
        private Double score;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillGrowthPoint {
        private String label;
        private int strongSkills;
        private int weakSkills;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistorySummary {
        private Long interviewId;
        private String date;
        private Double score;
        private String type;
        private String difficulty;
    }
}
