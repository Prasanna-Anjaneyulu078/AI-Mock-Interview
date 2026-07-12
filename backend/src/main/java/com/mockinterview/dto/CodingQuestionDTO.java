package com.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.mockinterview.entity.CodingQuestion;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodingQuestionDTO {
    private Long id;
    private Long interviewId;
    private String title;
    private String description;
    private String constraints;
    private String difficulty;
    private String starterCode;
    private String languageSupport;
    private Integer timeLimit;
    private Integer memoryLimit;
    private List<CodingTestCaseDTO> testCases;

    public static CodingQuestionDTO fromEntity(CodingQuestion question) {
        if (question == null) {
            return null;
        }
        return CodingQuestionDTO.builder()
                .id(question.getId())
                .interviewId(question.getInterview() != null ? question.getInterview().getId() : null)
                .title(question.getTitle())
                .description(question.getDescription())
                .constraints(question.getConstraints())
                .difficulty(question.getDifficulty())
                .starterCode(question.getStarterCode())
                .languageSupport(question.getLanguageSupport())
                .timeLimit(question.getTimeLimit())
                .memoryLimit(question.getMemoryLimit())
                .testCases(question.getTestCases() != null 
                    ? question.getTestCases().stream().map(CodingTestCaseDTO::fromEntity).collect(Collectors.toList())
                    : null)
                .build();
    }
}
