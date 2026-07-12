package com.mockinterview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.mockinterview.entity.CodingTestCase;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodingTestCaseDTO {
    private Long id;
    private String name;
    private String input;
    private String expectedOutput;

    // Explicit @JsonProperty to ensure Jackson serializes as "isHidden" (not "hidden")
    // Lombok Boolean getter is getIsHidden() which Jackson maps to "hidden" by default
    @JsonProperty("isHidden")
    private Boolean isHidden;

    public static CodingTestCaseDTO fromEntity(CodingTestCase testCase) {
        if (testCase == null) {
            return null;
        }
        return CodingTestCaseDTO.builder()
                .id(testCase.getId())
                .name(testCase.getName())
                .input(testCase.getInput())
                .expectedOutput(testCase.getExpectedOutput())
                .isHidden(testCase.getIsHidden())
                .build();
    }
}

