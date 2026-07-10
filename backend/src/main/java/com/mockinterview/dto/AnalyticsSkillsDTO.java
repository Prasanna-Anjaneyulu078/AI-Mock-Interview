package com.mockinterview.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Strong/weak skill signals and suggested improvement areas for the candidate. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSkillsDTO {
    private List<String> strongSkills;
    private List<String> weakSkills;
    private List<String> improvementAreas;
}
