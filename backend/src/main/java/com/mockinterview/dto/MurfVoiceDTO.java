package com.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Lightweight description of a Murf voice, returned to the frontend voice picker.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MurfVoiceDTO {
    private String voiceId;
    private String name;
    private String gender;
    private String locale;
    private String language;
    private List<String> styles;
}
