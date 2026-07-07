package com.mockinterview.dto;

import lombok.Data;

@Data
public class QuestionDTO {
    private Long id;
    private String text; // Map from questionText
    private String type;
    private Boolean isCodeQuestion;
    private String codeType;
    private String codeSnippet;
    private String codeLanguage;
}

