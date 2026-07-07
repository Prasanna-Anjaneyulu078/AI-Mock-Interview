package com.mockinterview.mapper;

import com.mockinterview.dto.InterviewResponse;
import com.mockinterview.entity.Interview;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {QuestionMapper.class})
public interface InterviewMapper {
    @Mapping(source = "id", target = "interviewId")
    @Mapping(source = "score", target = "overallScore")
    @Mapping(source = "interviewType", target = "interviewType")
    InterviewResponse toDTO(Interview interview);
}
