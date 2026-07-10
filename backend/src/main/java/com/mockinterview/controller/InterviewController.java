package com.mockinterview.controller;

import com.mockinterview.dto.*;
import com.mockinterview.entity.User;
import com.mockinterview.exception.ResourceNotFoundException;
import com.mockinterview.repository.UserRepository;
import com.mockinterview.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/interview")
@Tag(name = "Interview", description = "Endpoints for conducting interviews")
public class InterviewController {

    private final InterviewService interviewService;
    private final UserRepository userRepository;

    public InterviewController(InterviewService interviewService, UserRepository userRepository) {
        this.interviewService = interviewService;
        this.userRepository = userRepository;
    }

    private User getUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @PostMapping("/start")
    @Operation(summary = "Start a new interview")
    public ResponseEntity<ApiResponse<InterviewResponse>> startInterview(@Valid @RequestBody InterviewRequest request, Authentication authentication) {
        User user = getUser(authentication);
        InterviewResponse response = interviewService.startInterview(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Interview started", response));
    }

    @PostMapping("/{id}/answer")
    @Operation(summary = "Submit a text answer")
    public ResponseEntity<ApiResponse<AnswerResponse>> submitAnswer(@PathVariable Long id, @RequestBody AnswerRequest request, Authentication authentication) {
        User user = getUser(authentication);
        AnswerResponse response = interviewService.submitAnswer(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Answer processed", response));
    }

    @PostMapping("/{id}/end")
    @Operation(summary = "End interview and get feedback")
    public ResponseEntity<ApiResponse<FeedbackResponse>> endInterview(@PathVariable Long id, Authentication authentication) {
        User user = getUser(authentication);
        FeedbackResponse response = interviewService.endInterview(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Interview finished", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get interview details")
    public ResponseEntity<ApiResponse<InterviewResponse>> getInterview(@PathVariable Long id, Authentication authentication) {
        User user = getUser(authentication);
        InterviewResponse response = interviewService.getInterview(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Interview fetched", response));
    }

    @PostMapping("/{id}/code")
    @Operation(summary = "Submit a code answer (runs all test cases including hidden)")
    public ResponseEntity<ApiResponse<AnswerResponse>> submitCode(@PathVariable Long id, @RequestBody AnswerRequest request, Authentication authentication) {
        User user = getUser(authentication);
        AnswerResponse response = interviewService.submitAnswer(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Code processed", response));
    }

    @PostMapping("/{id}/run-code")
    @Operation(summary = "Run code against sample (visible) test cases only — does NOT advance the interview")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> runCode(
            @PathVariable Long id,
            @RequestBody AnswerRequest request,
            Authentication authentication) {
        User user = getUser(authentication);
        java.util.Map<String, Object> result = interviewService.runCodeSample(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Code executed", result));
    }


    @PostMapping("/transcribe")
    @Operation(summary = "Transcribe audio to text")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> transcribeOnly(@RequestParam("audio") org.springframework.web.multipart.MultipartFile audio) {
        java.util.Map<String, Object> transcriptionData = interviewService.transcribeAudio(audio);
        String text = (String) transcriptionData.getOrDefault("text", "");
        return ResponseEntity.ok(ApiResponse.success("Audio transcribed", java.util.Map.of("text", text)));
    }

    @PostMapping("/{id}/answer-audio")
    @Operation(summary = "Submit an audio answer")
    public ResponseEntity<ApiResponse<AnswerResponse>> submitVoiceAnswer(@PathVariable Long id, @RequestParam("audio") org.springframework.web.multipart.MultipartFile audio, Authentication authentication) {
        User user = getUser(authentication);
        AnswerResponse response = interviewService.submitVoiceAnswer(user.getId(), id, audio);
        return ResponseEntity.ok(ApiResponse.success("Voice answer processed", response));
    }

    @PostMapping("/{id}/speak")
    @Operation(summary = "Convert text to speech (Murf AI)")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> speakText(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        String audio = interviewService.speakText(request.get("text"));
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("audio", audio != null ? audio : "");
        return ResponseEntity.ok(ApiResponse.success("Speech generated", body));
    }
    @GetMapping("/{id}/welcome")
    @Operation(summary = "Get dynamic welcome introduction audio and text")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> getWelcomeIntroduction(@PathVariable Long id, Authentication authentication) {
        User user = getUser(authentication);
        java.util.Map<String, String> response = interviewService.generateWelcomeIntroduction(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Welcome generated", response));
    }
}
