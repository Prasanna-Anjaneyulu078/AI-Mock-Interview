package com.mockinterview.controller;

import com.mockinterview.dto.ApiResponse;
import com.mockinterview.entity.User;
import com.mockinterview.exception.ResourceNotFoundException;
import com.mockinterview.repository.UserRepository;
import com.mockinterview.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/history")
@Tag(name = "History", description = "Endpoints for managing user interview history")
public class HistoryController {

    private final HistoryService historyService;
    private final UserRepository userRepository;

    public HistoryController(HistoryService historyService, UserRepository userRepository) {
        this.historyService = historyService;
        this.userRepository = userRepository;
    }

    private User getUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @GetMapping
    @Operation(summary = "Get paginated interview history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        User user = getUser(authentication);
        Map<String, Object> history = historyService.getUserHistory(user.getId(), page, limit);
        return ResponseEntity.ok(ApiResponse.success("History fetched", history));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get specific interview history item")
    public ResponseEntity<ApiResponse<Object>> getHistoryItem(@PathVariable Long id, Authentication authentication) {
        User user = getUser(authentication);
        Object entry = historyService.getHistoryEntry(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success("History item fetched", entry));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete specific interview from history")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteHistoryItem(@PathVariable Long id, Authentication authentication) {
        User user = getUser(authentication);
        historyService.deleteHistoryEntry(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Interview deleted", Map.of("message", "Interview deleted")));
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Clear all interview history for user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearHistory(Authentication authentication) {
        User user = getUser(authentication);
        Map<String, Object> result = historyService.clearUserHistory(user.getId());
        return ResponseEntity.ok(ApiResponse.success("All history cleared", result));
    }
}
