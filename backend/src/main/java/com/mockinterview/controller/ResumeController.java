package com.mockinterview.controller;

import com.mockinterview.dto.ApiResponse;
import com.mockinterview.dto.ResumeResponse;
import com.mockinterview.entity.Resume;
import com.mockinterview.entity.User;
import com.mockinterview.exception.ResourceNotFoundException;
import com.mockinterview.repository.ResumeRepository;
import com.mockinterview.repository.UserRepository;
import com.mockinterview.service.ResumeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/resume")
@Tag(name = "Resume", description = "Endpoints for managing user resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;

    public ResumeController(ResumeService resumeService, ResumeRepository resumeRepository, UserRepository userRepository) {
        this.resumeService = resumeService;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload a new resume")
    public ResponseEntity<ApiResponse<ResumeResponse>> uploadResume(@RequestParam("file") MultipartFile file, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String extractedText = resumeService.parsePdf(file);

        Resume resume = Resume.builder()
                .user(user)
                .fileName(file.getOriginalFilename())
                .resumeText(extractedText)
                .build();
        resumeRepository.save(resume);

        ResumeResponse response = new ResumeResponse();
        response.setId(resume.getId());
        response.setFileName(resume.getFileName());
        response.setExtractedText(resume.getResumeText());
        response.setUploadedAt(resume.getUploadedAt());

        return ResponseEntity.ok(ApiResponse.success("Resume uploaded successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get the latest resume for user")
    public ResponseEntity<ApiResponse<ResumeResponse>> getResume(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        java.util.Optional<Resume> resumeOpt = resumeRepository.findFirstByUserIdOrderByUploadedAtDesc(user.getId());

        if (resumeOpt.isEmpty()) {
            // Return 200 with null data so the frontend can handle "no resume" gracefully
            return ResponseEntity.ok(ApiResponse.success("No resume found", null));
        }

        Resume resume = resumeOpt.get();
        ResumeResponse response = new ResumeResponse();
        response.setId(resume.getId());
        response.setFileName(resume.getFileName());
        response.setExtractedText(resume.getResumeText());
        response.setUploadedAt(resume.getUploadedAt());

        return ResponseEntity.ok(ApiResponse.success("Resume fetched", response));
    }
}
