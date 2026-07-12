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
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.mockinterview.repository.CodeSubmissionRepository codeSubmissionRepository;
    private final com.mockinterview.repository.CodingSubmissionRepository codingSubmissionRepository;
    private final com.mockinterview.repository.CodingResultRepository codingResultRepository;

    public AnalyticsService(InterviewHistoryRepository historyRepository,
                            InterviewRepository interviewRepository,
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                            com.mockinterview.repository.CodeSubmissionRepository codeSubmissionRepository,
                            com.mockinterview.repository.CodingSubmissionRepository codingSubmissionRepository,
                            com.mockinterview.repository.CodingResultRepository codingResultRepository) {
        this.historyRepository = historyRepository;
        this.interviewRepository = interviewRepository;
        this.objectMapper = objectMapper;
        this.codeSubmissionRepository = codeSubmissionRepository;
        this.codingSubmissionRepository = codingSubmissionRepository;
        this.codingResultRepository = codingResultRepository;
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

        // Calculate radar data (Strengths & Weaknesses via categories)
        List<com.mockinterview.entity.Interview> userInterviews = interviewRepository.findByUserId(userId);
        double comm = 0, tech = 0, proj = 0, code = 0, conf = 0;
        int countWithScores = 0;
        int passCount = 0;
        int evaluatedCount = 0;
        
        for (com.mockinterview.entity.Interview inv : userInterviews) {
            if (inv.getFeedback() != null && !inv.getFeedback().isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fb = (Map<String, Object>) objectMapper.readValue(inv.getFeedback(), Map.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cats = (Map<String, Object>) fb.get("categoryScores");
                    if (cats != null) {
                        comm += getScoreFromCat(cats, "communicationScore", "communicationSkills");
                        tech += getScoreFromCat(cats, "technicalScore", "technicalKnowledge");
                        proj += getScoreFromCat(cats, "problemSolvingScore", "projectScore");
                        code += getScoreFromCat(cats, "codingScore", "codeQuality");
                        conf += getScoreFromCat(cats, "confidenceScore", "confidence");
                        countWithScores++;
                    }
                    if (Boolean.TRUE.equals(fb.get("evaluated"))) {
                        evaluatedCount++;
                        String hr = (String) fb.get("hiringRecommendation");
                        if ("Hire".equalsIgnoreCase(hr) || "Borderline".equalsIgnoreCase(hr)) {
                            passCount++;
                        }
                    }
                } catch (Exception e) {
                    // Ignore parsing errors for individual interviews
                }
            }
        }

        double passRate = evaluatedCount > 0 ? (passCount * 100.0 / evaluatedCount) : 0.0;

        List<AnalyticsSummaryDTO.RadarPoint> radarData = new ArrayList<>();
        if (countWithScores > 0) {
            radarData.add(new AnalyticsSummaryDTO.RadarPoint("Communication", round1(comm / countWithScores)));
            radarData.add(new AnalyticsSummaryDTO.RadarPoint("Technical", round1(tech / countWithScores)));
            radarData.add(new AnalyticsSummaryDTO.RadarPoint("Project", round1(proj / countWithScores)));
            radarData.add(new AnalyticsSummaryDTO.RadarPoint("Code Quality", round1(code / countWithScores)));
            radarData.add(new AnalyticsSummaryDTO.RadarPoint("Confidence", round1(conf / countWithScores)));
        }

        // Calculate Language proficiency and Coding Accuracy
        List<Long> interviewIds = userInterviews.stream().map(com.mockinterview.entity.Interview::getId).collect(Collectors.toList());
        Map<String, Integer> languageCounts = new HashMap<>();
        long totalTests = 0;
        long passedTests = 0;

        for (Long iId : interviewIds) {
            // Legacy CodeSubmissions
            List<com.mockinterview.entity.CodeSubmission> subs = codeSubmissionRepository.findByInterviewId(iId);
            for (com.mockinterview.entity.CodeSubmission sub : subs) {
                if (sub.getLanguage() != null) {
                    languageCounts.merge(sub.getLanguage().toLowerCase(), 1, Integer::sum);
                }
                if (sub.getTotalTests() != null && sub.getTotalTests() > 0) {
                    totalTests += sub.getTotalTests();
                    passedTests += (sub.getPassedTests() != null ? sub.getPassedTests() : 0);
                }
            }
            // New CodingSubmissions
            List<com.mockinterview.entity.CodingSubmission> newSubs = codingSubmissionRepository.findByInterviewId(iId);
            for (com.mockinterview.entity.CodingSubmission sub : newSubs) {
                if (sub.getLanguage() != null) {
                    languageCounts.merge(sub.getLanguage().toLowerCase(), 1, Integer::sum);
                }
                com.mockinterview.entity.CodingResult res = codingResultRepository.findBySubmissionId(sub.getId()).orElse(null);
                if (res != null && res.getTotalTests() != null && res.getTotalTests() > 0) {
                    totalTests += res.getTotalTests();
                    passedTests += (res.getPassedTests() != null ? res.getPassedTests() : 0);
                }
            }
        }

        double codingAccuracy = totalTests > 0 ? (passedTests * 100.0 / totalTests) : 0.0;

        List<AnalyticsSummaryDTO.BarPoint> languageData = languageCounts.entrySet().stream()
                .map(e -> new AnalyticsSummaryDTO.BarPoint(e.getKey(), e.getValue()))
                .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount())) // Descending
                .collect(Collectors.toList());

        return AnalyticsSummaryDTO.builder()
                .totalInterviews((int) total)
                .averageScore(round1(average))
                .bestScore(round1(best))
                .completionRate(round1(completionRate))
                .codingAccuracy(round1(codingAccuracy))
                .passRate(round1(passRate))
                .radarData(radarData)
                .languageData(languageData)
                .build();
    }

    private double getScoreFromCat(Map<String, Object> cats, String key, String fallbackKey) {
        Object cat = cats.get(key);
        if (cat == null) cat = cats.get(fallbackKey);
        if (cat instanceof Map) {
            Object score = ((Map<?, ?>) cat).get("score");
            if (score instanceof Number) {
                return ((Number) score).doubleValue();
            }
        }
        return 0.0;
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
        List<AnalyticsProgressDTO.TrendPoint> difficultyProgression = new ArrayList<>();
        
        Map<String, List<Double>> monthScores = new TreeMap<>();

        Set<String> seenStrong = new LinkedHashSet<>();
        Set<String> seenWeak = new LinkedHashSet<>();

        Map<String, Integer> difficultyMap = Map.of("BEGINNER", 1, "INTERMEDIATE", 2, "ADVANCED", 3);

        for (InterviewHistory h : histories) {
            String label = h.getCreatedAt() != null ? h.getCreatedAt().toLocalDate().toString() : "";
            Double score = h.getTotalScore();

            performanceTrend.add(new AnalyticsProgressDTO.TrendPoint(label, score));

            if (h.getCreatedAt() != null && score != null) {
                String monthLabel = h.getCreatedAt().getYear() + "-" + String.format("%02d", h.getCreatedAt().getMonthValue());
                monthScores.computeIfAbsent(monthLabel, k -> new ArrayList<>()).add(score);
            }

            for (String s : parseSkills(h.getStrongSkills())) seenStrong.add(s.toLowerCase());
            for (String s : parseSkills(h.getWeakSkills())) seenWeak.add(s.toLowerCase());
            skillGrowthTrend.add(new AnalyticsProgressDTO.SkillGrowthPoint(
                    label, seenStrong.size(), seenWeak.size()));

            String type = null, difficulty = null, aiProviderUsed = null, providerError = null, interviewSource = null;
            Boolean fallbackActivated = false;
            if (h.getInterview() != null) {
                type = h.getInterview().getInterviewType();
                difficulty = h.getInterview().getDifficulty();
                fallbackActivated = h.getInterview().getFallbackActivated();
                aiProviderUsed = h.getInterview().getAiProviderUsed();
                providerError = h.getInterview().getProviderError();
                interviewSource = h.getInterview().getInterviewSource();
                if (difficulty != null) {
                    difficultyProgression.add(new AnalyticsProgressDTO.TrendPoint(label, (double) difficultyMap.getOrDefault(difficulty.toUpperCase(), 2)));
                }
            }
            history.add(new AnalyticsProgressDTO.HistorySummary(
                    h.getInterview() != null ? h.getInterview().getId() : null,
                    label, score, type, difficulty, fallbackActivated, aiProviderUsed, providerError, interviewSource));
        }

        List<AnalyticsProgressDTO.TrendPoint> monthlyTrends = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : monthScores.entrySet()) {
            double avg = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            monthlyTrends.add(new AnalyticsProgressDTO.TrendPoint(entry.getKey(), round1(avg)));
        }

        return AnalyticsProgressDTO.builder()
                .performanceTrend(performanceTrend)
                .skillGrowthTrend(skillGrowthTrend)
                .difficultyProgression(difficultyProgression)
                .monthlyTrends(monthlyTrends)
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
