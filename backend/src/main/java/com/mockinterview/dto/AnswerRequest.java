package com.mockinterview.dto;

import lombok.Data;

@Data
public class AnswerRequest {
    private String answerText;
    private String code;
    private String language;
}
