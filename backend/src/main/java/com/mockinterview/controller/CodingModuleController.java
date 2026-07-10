package com.mockinterview.controller;

import com.mockinterview.entity.CodingQuestion;
import com.mockinterview.entity.CodingResult;
import com.mockinterview.entity.CodingSubmission;
import com.mockinterview.service.CodingModuleService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coding-module")
public class CodingModuleController {

    private final CodingModuleService codingModuleService;

    public CodingModuleController(CodingModuleService codingModuleService) {
        this.codingModuleService = codingModuleService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startCodingModule(@RequestBody StartCodingRequest request) {
        try {
            CodingQuestion question = codingModuleService.generateCodingQuestion(
                    request.getInterviewId()
            );
            return ResponseEntity.ok(question);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/{questionId}/submit")
    public ResponseEntity<?> submitCode(@PathVariable Long questionId, @RequestBody SubmitCodeRequest request) {
        try {
            CodingSubmission submission = codingModuleService.submitCode(
                    questionId,
                    request.getSourceCode(),
                    request.getLanguage()
            );
            return ResponseEntity.ok(submission);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/{questionId}/run")
    public ResponseEntity<?> runCode(@PathVariable Long questionId, @RequestBody SubmitCodeRequest request) {
        try {
            com.mockinterview.service.Judge0Result result = codingModuleService.runSampleCode(
                    questionId,
                    request.getSourceCode(),
                    request.getLanguage()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/evaluate/{submissionId}")
    public ResponseEntity<?> evaluateSubmission(@PathVariable Long submissionId) {
        try {
            CodingResult result = codingModuleService.evaluateSubmission(submissionId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ErrorResponse(e.getMessage()));
        }
    }

    @Data
    static class StartCodingRequest {
        private Long interviewId;
        private String role;
        private String resumeSkills;
        private String userInterests;
        private String difficulty;
    }

    @Data
    static class SubmitCodeRequest {
        private String sourceCode;
        private String language;
    }

    @Data
    static class ErrorResponse {
        private final String message;
    }
}
