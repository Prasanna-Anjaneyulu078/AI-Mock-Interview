package com.mockinterview.service;

import com.mockinterview.service.ai.AIProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Validates answers before they are scored.
 * Phase 3: Empty/blank/whitespace answers are now ALLOWED to pass through —
 * the ScoringService will detect them and assign score=0, feedback="Question not answered".
 * This enables explicit question-skipping without a hard error.
 */
@Service
public class AnswerValidationService {

    private static final Logger log = LoggerFactory.getLogger(AnswerValidationService.class);
    private final AIProvider aiProvider;
    private static final Pattern SPAM_PATTERN = Pattern.compile("^(.)\\1{4,}$");

    public AnswerValidationService(AIProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public void validateAnswer(String answer, String questionType) {
        // Phase 3: Allow empty/blank answers — they will be treated as "skipped" by ScoringService.
        if (answer == null || answer.isBlank()) {
            log.debug("Empty answer received for question type '{}' — will be scored as skipped.", questionType);
            return;
        }

        String trimmed = answer.trim();

        // Reject obvious spam (e.g. "aaaaaaa")
        if (SPAM_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Repeated or invalid text detected. Please answer the question properly.");
        }

        // For very short non-code answers, require at minimum a real word
        if (trimmed.length() < 5 && !trimmed.matches("(?i)^(yes|no|ok|hi|hello|true|false)$")) {
            throw new IllegalArgumentException("Your answer does not appear to be meaningful. Please provide a valid response.");
        }

        // AI anti-spam check for borderline cases (short text answers)
        if (trimmed.length() >= 5 && trimmed.length() < 100) {
            try {
                String aiEval = aiProvider.validateAnswer(trimmed, questionType);
                if (aiEval != null && aiEval.strip().equalsIgnoreCase("INVALID")) {
                    throw new IllegalArgumentException("Your answer does not appear to be meaningful. Please provide a valid response.");
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.warn("AI answer validation call failed (non-critical): {}", e.getMessage());
            }
        }
    }
}
