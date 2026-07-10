package com.mockinterview.dto;

import lombok.Data;

@Data
public class AnswerRequest {
    private String answerText;
    private String code;
    private String language;
    private Integer audioDurationSeconds;
    private Integer fillerWordsCount;
    private Double speakingSpeed;
    private String recordingUrl;
    private Integer responseTimeSeconds;
}
