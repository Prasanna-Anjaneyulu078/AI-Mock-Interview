package com.mockinterview.controller;

import com.mockinterview.dto.ApiResponse;
import com.mockinterview.dto.AnalyticsProgressDTO;
import com.mockinterview.dto.AnalyticsSkillsDTO;
import com.mockinterview.dto.AnalyticsSummaryDTO;
import com.mockinterview.entity.User;
import com.mockinterview.exception.ResourceNotFoundException;
import com.mockinterview.repository.UserRepository;
import com.mockinterview.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Candidate performance dashboard APIs")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;

    public AnalyticsController(AnalyticsService analyticsService, UserRepository userRepository) {
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
    }

    private User getUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @GetMapping("/summary")
    @Operation(summary = "Overall performance summary (interviews, average/best score, completion rate)")
    public ResponseEntity<ApiResponse<AnalyticsSummaryDTO>> getSummary(Authentication authentication) {
        User user = getUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Summary", analyticsService.getSummary(user.getId())));
    }

    @GetMapping("/skills")
    @Operation(summary = "Strong/weak skills and improvement areas")
    public ResponseEntity<ApiResponse<AnalyticsSkillsDTO>> getSkills(Authentication authentication) {
        User user = getUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Skills", analyticsService.getSkills(user.getId())));
    }

    @GetMapping("/progress")
    @Operation(summary = "Performance + skill-growth trends and per-interview history")
    public ResponseEntity<ApiResponse<AnalyticsProgressDTO>> getProgress(Authentication authentication) {
        User user = getUser(authentication);
        return ResponseEntity.ok(ApiResponse.success("Progress", analyticsService.getProgress(user.getId())));
    }
}
