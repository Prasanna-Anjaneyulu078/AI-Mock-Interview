package com.mockinterview.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Voice customization options for Murf text-to-speech.
 * Fields map directly to the Murf generate-speech API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MurfVoiceOptions {

    /** Murf voice id, e.g. "en-US-natalie". Null => service default. */
    private String voiceId;

    /** Voice style, e.g. "Conversational", "Promo". Null => service default. */
    private String style;

    /** Speech rate, Murf scale -50..50 (0 = normal speed). Null => default. */
    private Integer rate;

    /** Pitch, Murf scale -50..50 (0 = default). Null => default. */
    private Integer pitch;

    public static MurfVoiceOptions defaults() {
        return MurfVoiceOptions.builder().build();
    }
}
