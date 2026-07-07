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

@RestController
@RequestMapping("/api/interview")
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
    public ResponseEntity<ApiResponse<InterviewResponse>> startInterview(@Valid @RequestBody InterviewRequest request, Authentication authentication) {
        User user = getUser(authentication);
        InterviewResponse response = interviewService.startInterview(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Interview started", response));
    }

    @PostMapping("/{id}/answer")
    public ResponseEntity<ApiResponse<AnswerResponse>> submitAnswer(@PathVariable Long id, @RequestBody AnswerRequest request, Authentication authentication) {
        User user = getUser(authentication);
        AnswerResponse response = interviewService.submitAnswer(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Answer processed", response));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<ApiResponse<FeedbackResponse>> endInterview(@PathVariable Long id, Authentication authentication) {
        User user = getUser(authentication);
        FeedbackResponse response = interviewService.endInterview(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Interview finished", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InterviewResponse>> getInterview(@PathVariable Long id, Authentication authentication) {
        User user = getUser(authentication);
        InterviewResponse response = interviewService.getInterview(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Interview fetched", response));
    }

    @PostMapping("/{id}/code")
    public ResponseEntity<ApiResponse<AnswerResponse>> submitCode(@PathVariable Long id, @RequestBody AnswerRequest request, Authentication authentication) {
        User user = getUser(authentication);
        AnswerResponse response = interviewService.submitAnswer(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Code processed", response));
    }

    @PostMapping("/transcribe")
    public ResponseEntity<ApiResponse<String>> transcribeOnly(@RequestParam("audio") org.springframework.web.multipart.MultipartFile audio) {
        String text = interviewService.transcribeAudio(audio);
        return ResponseEntity.ok(ApiResponse.success("Audio transcribed", text));
    }

    @PostMapping("/{id}/answer-audio")
    public ResponseEntity<ApiResponse<AnswerResponse>> submitVoiceAnswer(@PathVariable Long id, @RequestParam("audio") org.springframework.web.multipart.MultipartFile audio, Authentication authentication) {
        User user = getUser(authentication);
        AnswerResponse response = interviewService.submitVoiceAnswer(user.getId(), id, audio);
        return ResponseEntity.ok(ApiResponse.success("Voice answer processed", response));
    }

    @PostMapping("/{id}/speak")
    public ResponseEntity<org.springframework.core.io.Resource> speakText(@PathVariable Long id, @RequestBody java.util.Map<String, String> request) {
        org.springframework.core.io.Resource audioResource = interviewService.speakText(request.get("text"));
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("audio/mpeg"))
                .body(audioResource);
    }
}
