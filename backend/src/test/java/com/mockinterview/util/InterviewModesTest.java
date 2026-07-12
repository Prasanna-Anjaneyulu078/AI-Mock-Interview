package com.mockinterview.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterviewModesTest {

    @Test
    void normalizesLegacyAliasesToCanonical() {
        assertEquals(InterviewModes.CODING, InterviewModes.normalize("CODING_INTERVIEW"));
        assertEquals(InterviewModes.RESUME_BASED, InterviewModes.normalize("RESUME"));
        assertEquals(InterviewModes.BEHAVIORAL, InterviewModes.normalize("HR"));
        assertEquals(InterviewModes.PROJECT, InterviewModes.normalize("PROJECT"));
    }

    @Test
    void passesCanonicalModesThroughUnchanged() {
        assertEquals(InterviewModes.CODING, InterviewModes.normalize("CODING"));
        assertEquals(InterviewModes.TECHNICAL, InterviewModes.normalize("TECHNICAL"));
        assertEquals(InterviewModes.BEHAVIORAL, InterviewModes.normalize("BEHAVIORAL"));
        assertEquals(InterviewModes.RESUME_BASED, InterviewModes.normalize("RESUME_BASED"));
    }

    @Test
    void unknownAndNullModesFallBackToResumeBased() {
        assertEquals(InterviewModes.RESUME_BASED, InterviewModes.normalize(null));
        assertEquals(InterviewModes.RESUME_BASED, InterviewModes.normalize("   "));
        // Unknown values are preserved as-is (no data loss for future modes).
        assertEquals("FUTURE_MODE", InterviewModes.normalize("FUTURE_MODE"));
    }

    @Test
    void resumeRequirementIsEnforcedOnlyForResumeBased() {
        assertTrue(InterviewModes.requiresResume(InterviewModes.RESUME_BASED));
        assertFalse(InterviewModes.requiresResume(InterviewModes.CODING));
        assertFalse(InterviewModes.requiresResume(InterviewModes.TECHNICAL));
        assertFalse(InterviewModes.requiresResume(InterviewModes.BEHAVIORAL));
    }

    @Test
    void voiceIsOfferedForAllModesExceptCoding() {
        assertFalse(InterviewModes.supportsVoice(InterviewModes.CODING));
        assertTrue(InterviewModes.supportsVoice(InterviewModes.RESUME_BASED));
        assertTrue(InterviewModes.supportsVoice(InterviewModes.TECHNICAL));
    }
}
