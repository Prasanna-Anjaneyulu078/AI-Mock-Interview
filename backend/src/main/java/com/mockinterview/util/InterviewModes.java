package com.mockinterview.util;

import java.util.Map;
import java.util.Set;

/**
 * Canonical interview-mode vocabulary shared across the backend.
 *
 * <p>The frontend and older clients historically used a wider set of mode strings
 * ({@code CODING_INTERVIEW}, {@code RESUME}, {@code HR}, {@code PROJECT},
 * {@code INTEREST_BASED}, {@code HYBRID}, {@code MIXED}). To keep the rest of the
 * codebase (question generation, prompt building, validation) simple and consistent
 * we normalize every incoming mode to one of the canonical values below. Legacy
 * values are preserved as aliases so already-persisted interviews and old clients
 * keep working.
 *
 * <p>Canonical UI modes (the only ones the redesigned Setup UI exposes):
 * <ul>
 *   <li>{@code CODING}</li>
 *   <li>{@code TECHNICAL}</li>
 *   <li>{@code BEHAVIORAL}</li>
 *   <li>{@code RESUME_BASED}</li>
 * </ul>
 */
public final class InterviewModes {

    // ── Canonical modes surfaced by the UI ──
    public static final String CODING = "CODING";
    public static final String TECHNICAL = "TECHNICAL";
    public static final String BEHAVIORAL = "BEHAVIORAL";
    public static final String RESUME_BASED = "RESUME_BASED";

    // ── Legacy modes retained for backward compatibility ──
    public static final String PROJECT = "PROJECT";
    public static final String INTEREST_BASED = "INTEREST_BASED";
    public static final String HYBRID = "HYBRID";
    public static final String MIXED = "MIXED";

    /** Alias → canonical. Anything not listed maps to itself (so unknown values pass through). */
    private static final Map<String, String> ALIASES = Map.of(
            "CODING_INTERVIEW", CODING,
            "RESUME", RESUME_BASED,
            "HR", BEHAVIORAL,
            "PROJECT", PROJECT,
            "INTEREST_BASED", INTEREST_BASED,
            "HYBRID", HYBRID
    );

    private static final Set<String> ALL_KNOWN = Set.of(
            CODING, TECHNICAL, BEHAVIORAL, RESUME_BASED,
            PROJECT, INTEREST_BASED, HYBRID, MIXED
    );

    private InterviewModes() {}

    /**
     * Normalize an incoming mode string to its canonical form.
     *
     * @param raw the raw mode from the request (may be null or a legacy alias)
     * @return canonical mode, or {@code RESUME_BASED} when {@code raw} is null/blank
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return RESUME_BASED;
        }
        String trimmed = raw.trim();
        return ALIASES.getOrDefault(trimmed, trimmed);
    }

    /** True when the mode is one of the four UI-facing canonical modes. */
    public static boolean isCanonicalUiMode(String mode) {
        return mode != null && Set.of(CODING, TECHNICAL, BEHAVIORAL, RESUME_BASED).contains(mode);
    }

    /** True when a resume upload is mandatory for this mode. */
    public static boolean requiresResume(String mode) {
        return RESUME_BASED.equals(mode);
    }

    /** True for modes where the voice interviewer is offered. */
    public static boolean supportsVoice(String mode) {
        return !CODING.equals(mode);
    }

    /** Exposed for tests / documentation. */
    public static Set<String> knownModes() {
        return ALL_KNOWN;
    }
}
