package com.mockinterview.controller;

import com.mockinterview.dto.ApiResponse;
import com.mockinterview.dto.ResumeResponse;
import com.mockinterview.entity.Resume;
import com.mockinterview.entity.User;
import com.mockinterview.exception.ResourceNotFoundException;
import com.mockinterview.repository.ResumeRepository;
import com.mockinterview.repository.UserRepository;
import com.mockinterview.service.ResumeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@RestController
@RequestMapping("/api/resume")
@Tag(name = "Resume", description = "Endpoints for managing user resumes")
public class ResumeController {

    private final ResumeService resumeService;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.upload.max-file-size:10MB}")
    private String maxFileSize;

    @Value("${app.upload.allowed-types:application/pdf}")
    private List<String> allowedTypes;

    public ResumeController(ResumeService resumeService, ResumeRepository resumeRepository, UserRepository userRepository) {
        this.resumeService = resumeService;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload a new resume")
    public ResponseEntity<ApiResponse<ResumeResponse>> uploadResume(@RequestParam("file") MultipartFile file, Authentication authentication) {
        // Validate file
        validateFile(file);

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String extractedText = resumeService.parsePdf(file);
        String structuredSkills = resumeService.analyzeResume(extractedText);

        Resume.ResumeBuilder builder = Resume.builder()
                .user(user)
                .fileName(file.getOriginalFilename())
                .resumeText(extractedText)
                .structuredSkills(structuredSkills);
        applyProfile(builder, structuredSkills);
        Resume resume = builder.build();
        resumeRepository.save(resume);

        ResumeResponse response = new ResumeResponse();
        response.setId(resume.getId());
        response.setFileName(resume.getFileName());
        response.setExtractedText(resume.getResumeText());
        response.setStructuredSkills(resume.getStructuredSkills());
        response.setSkills(resume.getSkills());
        response.setTechnologies(resume.getTechnologies());
        response.setFrameworks(resume.getFrameworks());
        response.setLanguages(resume.getLanguages());
        response.setProjects(resume.getProjects());
        response.setEducation(resume.getEducation());
        response.setExperience(resume.getExperience());
        response.setCertifications(resume.getCertifications());
        response.setAchievements(resume.getAchievements());
        response.setDomainsOfExpertise(resume.getDomainsOfExpertise());
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
        response.setStructuredSkills(resume.getStructuredSkills());
        response.setSkills(resume.getSkills());
        response.setTechnologies(resume.getTechnologies());
        response.setFrameworks(resume.getFrameworks());
        response.setLanguages(resume.getLanguages());
        response.setProjects(resume.getProjects());
        response.setEducation(resume.getEducation());
        response.setExperience(resume.getExperience());
        response.setCertifications(resume.getCertifications());
        response.setAchievements(resume.getAchievements());
        response.setDomainsOfExpertise(resume.getDomainsOfExpertise());
        response.setUploadedAt(resume.getUploadedAt());

        return ResponseEntity.ok(ApiResponse.success("Resume fetched", response));
    }

    @GetMapping("/list")
    @Operation(summary = "Get all resumes for user")
    public ResponseEntity<ApiResponse<List<ResumeResponse>>> listResumes(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Resume> resumes = resumeRepository.findByUserId(user.getId());

        List<ResumeResponse> responseList = resumes.stream()
                .map(resume -> {
                    ResumeResponse response = new ResumeResponse();
                    response.setId(resume.getId());
                    response.setFileName(resume.getFileName());
                    response.setExtractedText(resume.getResumeText());
                    response.setStructuredSkills(resume.getStructuredSkills());
                    response.setUploadedAt(resume.getUploadedAt());
                    return response;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Resumes fetched", responseList));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get specific resume by ID")
    public ResponseEntity<ApiResponse<ResumeResponse>> getResumeById(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Resume resume = resumeRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        ResumeResponse response = new ResumeResponse();
        response.setId(resume.getId());
        response.setFileName(resume.getFileName());
        response.setExtractedText(resume.getResumeText());
        response.setStructuredSkills(resume.getStructuredSkills());
        response.setSkills(resume.getSkills());
        response.setTechnologies(resume.getTechnologies());
        response.setFrameworks(resume.getFrameworks());
        response.setLanguages(resume.getLanguages());
        response.setProjects(resume.getProjects());
        response.setEducation(resume.getEducation());
        response.setExperience(resume.getExperience());
        response.setCertifications(resume.getCertifications());
        response.setAchievements(resume.getAchievements());
        response.setDomainsOfExpertise(resume.getDomainsOfExpertise());
        response.setUploadedAt(resume.getUploadedAt());

        return ResponseEntity.ok(ApiResponse.success("Resume fetched", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete specific resume")
    public ResponseEntity<ApiResponse<String>> deleteResume(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Resume resume = resumeRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        resumeRepository.delete(resume);

        return ResponseEntity.ok(ApiResponse.success("Resume deleted", "Resume deleted successfully"));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new IllegalArgumentException("Invalid file type. Only PDF files are allowed.");
        }

        // Validate file name
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }

        // Check for malicious file names
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name");
        }

        // Validate file size (parse from config, default 10MB)
        long maxSize = parseMaxFileSize(maxFileSize);
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " + maxFileSize);
        }
    }

    private long parseMaxFileSize(String size) {
        size = size.toUpperCase().trim();
        if (size.endsWith("MB")) {
            return Long.parseLong(size.substring(0, size.length() - 2)) * 1024 * 1024;
        } else if (size.endsWith("KB")) {
            return Long.parseLong(size.substring(0, size.length() - 2)) * 1024;
        } else if (size.endsWith("GB")) {
            return Long.parseLong(size.substring(0, size.length() - 2)) * 1024 * 1024 * 1024;
        }
        return 10 * 1024 * 1024; // Default 10MB
    }

    /** Parse the 10-field profile JSON and populate the dedicated builder columns. */
    private void applyProfile(Resume.ResumeBuilder builder, String profileJson) {
        builder.skills(field(profileJson, "skills"));
        builder.technologies(field(profileJson, "technologies"));
        builder.frameworks(field(profileJson, "frameworks"));
        builder.languages(field(profileJson, "languages"));
        builder.projects(field(profileJson, "projects"));
        builder.education(field(profileJson, "education"));
        builder.experience(field(profileJson, "experience"));
        builder.certifications(field(profileJson, "certifications"));
        builder.achievements(field(profileJson, "achievements"));
        builder.domainsOfExpertise(field(profileJson, "domainsOfExpertise"));
    }

    /** Extract a single array field from the profile JSON as a JSON-array string, or null. */
    private String field(String json, String key) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode node = root.get(key);
            if (node != null && node.isArray()) {
                return node.toString();
            }
        } catch (Exception ignored) {
            // malformed profile JSON — leave field null
        }
        return null;
    }
}
