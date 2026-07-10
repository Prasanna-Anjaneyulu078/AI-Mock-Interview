package com.mockinterview.service;

import org.springframework.stereotype.Service;

/**
 * Text-to-speech orchestration. Delegates to {@link MurfService} for real speech
 * synthesis and returns {@code null} when TTS is unavailable so callers can continue
 * without audio (graceful degradation).
 */
@Service
public class TextToSpeechService {

    private final MurfService murfService;

    public TextToSpeechService(MurfService murfService) {
        this.murfService = murfService;
    }

    /**
     * @return audio URL (or base64 data URL), or {@code null} if TTS is unavailable.
     */
    public String synthesizeSpeech(String text) {
        return murfService.generateSpeech(text);
    }

    public String synthesizeSpeech(String text, MurfVoiceOptions options) {
        return murfService.generateSpeech(text, options);
    }
}
