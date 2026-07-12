package com.mockinterview.controller;

import com.mockinterview.entity.CodingQuestion;
import com.mockinterview.entity.CodingResult;
import com.mockinterview.entity.CodingSubmission;
import com.mockinterview.entity.User;
import com.mockinterview.service.CodingModuleService;
import com.mockinterview.repository.UserRepository;
import com.mockinterview.exception.UnauthorizedException;
import com.mockinterview.dto.CodingQuestionDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/coding-module")
public class CodingModuleController {

    private final CodingModuleService codingModuleService;
    private final UserRepository userRepository;

    public CodingModuleController(CodingModuleService codingModuleService, UserRepository userRepository) {
        this.codingModuleService = codingModuleService;
        this.userRepository = userRepository;
    }

    private User getUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("User not authenticated");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }

    @PostMapping("/start")
    public ResponseEntity<?> startCodingModule(@Valid @RequestBody StartCodingRequest request, Authentication authentication) {
        User user = getUser(authentication);
        log.info("Starting coding module for interviewId: {} by user: {}", request.getInterviewId(), user.getId());
        CodingQuestion question = codingModuleService.generateCodingQuestion(
                request.getInterviewId(),
                user.getId()
        );
        return ResponseEntity.ok(CodingQuestionDTO.fromEntity(question));
    }

    @PostMapping("/{questionId}/submit")
    public ResponseEntity<?> submitCode(@PathVariable Long questionId, @Valid @RequestBody SubmitCodeRequest request, Authentication authentication) {
        User user = getUser(authentication);
        log.info("Submitting code for questionId: {} by user: {}", questionId, user.getId());
        CodingSubmission submission = codingModuleService.submitCode(
                questionId,
                request.getSourceCode(),
                request.getLanguage(),
                user.getId()
        );
        return ResponseEntity.ok(submission);
    }

    @PostMapping("/{questionId}/run")
    public ResponseEntity<?> runCode(@PathVariable Long questionId, @Valid @RequestBody SubmitCodeRequest request, Authentication authentication) {
        User user = getUser(authentication);
        log.info("Running sample code for questionId: {} by user: {}", questionId, user.getId());
        com.mockinterview.service.Judge0Result result = codingModuleService.runSampleCode(
                questionId,
                request.getSourceCode(),
                request.getLanguage(),
                user.getId()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/evaluate/{submissionId}")
    public ResponseEntity<?> evaluateSubmission(@PathVariable Long submissionId, Authentication authentication) {
        User user = getUser(authentication);
        log.info("Evaluating submissionId: {} by user: {}", submissionId, user.getId());
        CodingResult result = codingModuleService.evaluateSubmission(submissionId, user.getId());
        return ResponseEntity.ok(result);
    }

    @Data
    static class StartCodingRequest {
        @NotNull(message = "Interview ID is required")
        private Long interviewId;
        private String role;
        private String resumeSkills;
        private String userInterests;
        private String difficulty;
    }

    @Data
    static class SubmitCodeRequest {
        @NotBlank(message = "Source code cannot be empty")
        private String sourceCode;
        
        @NotBlank(message = "Language cannot be empty")
        private String language;
    }

    @Data
    static class ErrorResponse {
        private final String message;
    }
}
