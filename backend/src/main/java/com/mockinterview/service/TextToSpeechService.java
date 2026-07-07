package com.mockinterview.service;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class TextToSpeechService {

    public Resource synthesizeSpeech(String text) {
        // This is a simplified mock implementation for Murf / TTS.
        // Real implementation would call the TTS API and return an audio stream/file.
        String mockAudioContent = "Mock audio content for: " + text;
        return new ByteArrayResource(mockAudioContent.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "speech.mp3";
            }
        };
    }
}
