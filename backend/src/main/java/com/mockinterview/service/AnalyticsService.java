package com.mockinterview.service;

import com.mockinterview.dto.AnalyticsProgressDTO;
import com.mockinterview.dto.AnalyticsSkillsDTO;
import com.mockinterview.dto.AnalyticsSummaryDTO;
import com.mockinterview.entity.InterviewHistory;
import com.mockinterview.repository.InterviewHistoryRepository;
import com.mockinterview.repository.InterviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Candidate performance analytics (Project-scope §9). All aggregates are scoped to the
 * authenticated user: every figure is derived from that user's {@link InterviewHistory}
 * rows (which already fold in per-answer evaluations and strengths/weaknesses).
 */
@Service
public class AnalyticsService {

    private final InterviewHistoryRepository historyRepository;
    private final InterviewRepository interviewRepository;

    public AnalyticsService(InterviewHistoryRepository historyRepository,
                            InterviewRepository interviewRepository) {
        this.historyRepository = historyRepository;
        this.interviewRepository = interviewRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryDTO getSummary(Long userId) {
        long total = interviewRepository.countByUserId(userId);
        List<InterviewHistory> histories = historyRepository.findByUserIdOrderByCreatedAtDesc(userId);

        double average = histories.stream()
                .map(InterviewHistory::getTotalScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        double best = histories.stream()
                .map(InterviewHistory::getTotalScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        long completed = interviewRepository.countByUserIdAndStatus(userId, "completed");
        double completionRate = total > 0 ? (completed * 100.0 / total) : 0.0;

        return AnalyticsSummaryDTO.builder()
                .totalInterviews((int) total)
                .averageScore(round1(average))
                .bestScore(round1(best))
                .completionRate(round1(completionRate))
                .build();
    }

    @Transactional(readOnly = true)
    public AnalyticsSkillsDTO getSkills(Long userId) {
        List<InterviewHistory> histories = historyRepository.findByUserIdOrderByCreatedAtAsc(userId);
        Map<String, Integer> strong = new HashMap<>();
        Map<String, Integer> weak = new HashMap<>();
        Map<String, Integer> improve = new HashMap<>();
        for (InterviewHistory h : histories) {
            countSkills(parseSkills(h.getStrongSkills()), strong);
            countSkills(parseSkills(h.getWeakSkills()), weak);
            countSkills(parseSkills(h.getImprovements()), improve);
        }
        return AnalyticsSkillsDTO.builder()
                .strongSkills(topN(strong, 10))
                .weakSkills(topN(weak, 10))
                .improvementAreas(topN(improve, 10))
                .build();
    }

    @Transactional(readOnly = true)
    public AnalyticsProgressDTO getProgress(Long userId) {
        List<InterviewHistory> histories = historyRepository.findByUserIdOrderByCreatedAtAsc(userId);
        List<AnalyticsProgressDTO.TrendPoint> performanceTrend = new ArrayList<>();
        List<AnalyticsProgressDTO.SkillGrowthPoint> skillGrowthTrend = new ArrayList<>();
        List<AnalyticsProgressDTO.HistorySummary> history = new ArrayList<>();

        Set<String> seenStrong = new LinkedHashSet<>();
        Set<String> seenWeak = new LinkedHashSet<>();

        for (InterviewHistory h : histories) {
            String label = h.getCreatedAt() != null ? h.getCreatedAt().toLocalDate().toString() : "";
            Double score = h.getTotalScore();

            performanceTrend.add(new AnalyticsProgressDTO.TrendPoint(label, score));

            for (String s : parseSkills(h.getStrongSkills())) seenStrong.add(s.toLowerCase());
            for (String s : parseSkills(h.getWeakSkills())) seenWeak.add(s.toLowerCase());
            skillGrowthTrend.add(new AnalyticsProgressDTO.SkillGrowthPoint(
                    label, seenStrong.size(), seenWeak.size()));

            String type = null, difficulty = null;
            if (h.getInterview() != null) {
                type = h.getInterview().getInterviewType();
                difficulty = h.getInterview().getDifficulty();
            }
            history.add(new AnalyticsProgressDTO.HistorySummary(
                    h.getInterview() != null ? h.getInterview().getId() : null,
                    label, score, type, difficulty));
        }

        return AnalyticsProgressDTO.builder()
                .performanceTrend(performanceTrend)
                .skillGrowthTrend(skillGrowthTrend)
                .history(history)
                .build();
    }

    // ── helpers ──

    /** Parse a skill list stored either as JSON (["a","b"]) or Java List.toString() ([a, b]). */
    private List<String> parseSkills(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String inner = raw.trim();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);
        if (inner.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String token : inner.split(",")) {
            String t = token.trim();
            if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
                t = t.substring(1, t.length() - 1).trim();
            } else if (t.length() >= 2 && t.startsWith("'") && t.endsWith("'")) {
                t = t.substring(1, t.length() - 1).trim();
            }
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }

    private void countSkills(List<String> skills, Map<String, Integer> freq) {
        for (String s : skills) {
            freq.merge(s.toLowerCase(), 1, Integer::sum);
        }
    }

    /** Most frequent skills first, then alphabetical; capped at n. */
    private List<String> topN(Map<String, Integer> freq, int n) {
        return freq.entrySet().stream()
                .sorted((a, b) -> {
                    int c = b.getValue().compareTo(a.getValue());
                    return c != 0 ? c : a.getKey().compareTo(b.getKey());
                })
                .limit(n)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private double round1(double d) {
        return Math.round(d * 10.0) / 10.0;
    }
}
