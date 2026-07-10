package com.mockinterview.service;

import com.mockinterview.service.ai.AIProvider;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class ResumeService {

    private final AIProvider aiProvider;

    public static final List<String> PROFILE_FIELDS = List.of(
            "skills", "technologies", "frameworks", "languages",
            "projects", "education", "experience", "certifications",
            "achievements", "domainsOfExpertise"
    );

    private static final int FULL_TEXT_LIMIT = 60000;
    private static final int HARD_CAP = 60000;
    private static final int SECTION_SAMPLE = 4000;
    private static final int HEAD_KEEP = 30000;
    private static final int TAIL_KEEP = 15000;

    public ResumeService(AIProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public String parsePdf(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return text != null ? text.trim() : "";
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse PDF resume: " + e.getMessage());
        }
    }

    public String analyzeResume(String resumeText) {
        try {
            String result = aiProvider.analyzeResume(resumeText);
            
            if (result != null) {
                result = extractJson(result);
            }
            return result != null ? result : "{}";
        } catch (Exception e) {
            System.err.println("⚠️ AI failed to analyze resume: " + e.getMessage());
            return "{}";
        }
    }
    
    private String extractJson(String text) {
        if (text == null) return "{}";
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    public String prepareResumeContext(String resumeText, String structuredProfileJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== STRUCTURED CANDIDATE PROFILE ===\n");
        sb.append(structuredProfileJson != null && !structuredProfileJson.isBlank()
                ? structuredProfileJson : "{}");
        sb.append("\n\n=== FULL RESUME TEXT ===\n");
        if (resumeText == null || resumeText.isBlank()) {
            sb.append("Not provided");
        } else if (resumeText.length() <= FULL_TEXT_LIMIT) {
            sb.append(resumeText);
        } else {
            sb.append(chunkResume(resumeText));
        }
        return sb.toString();
    }

    private String chunkResume(String text) {
        String[] parts = text.split(
                "(?mi)(?=^\\s*(?:\\d+[.)]?\\s*)?(experience|work experience|employment|projects?|education|academic|certifications?|certificates|skills?|technical skills|achievements?|awards?|summary|profile|about)\\b)",
                -1);
        if (parts.length <= 2) {
            return text.substring(0, Math.min(HEAD_KEEP, text.length()))
                    + "\n... [middle condensed; structured profile above preserves key signals] ...\n"
                    + text.substring(Math.max(HEAD_KEEP, text.length() - TAIL_KEEP));
        }
        StringBuilder out = new StringBuilder();
        int used = 0;
        boolean overflow = false;
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (used + part.length() <= HARD_CAP) {
                out.append(part);
                used += part.length();
            } else {
                int room = HARD_CAP - used;
                if (room > 200) {
                    out.append(part, 0, Math.min(room, SECTION_SAMPLE));
                    out.append("\n... [section condensed] ...\n");
                    used += Math.min(room, SECTION_SAMPLE);
                }
                overflow = true;
                break;
            }
        }
        if (overflow) {
            out.append("\n... [remaining sections condensed; structured profile above preserves skills/projects/experience] ...\n");
        }
        return out.toString();
    }
}
