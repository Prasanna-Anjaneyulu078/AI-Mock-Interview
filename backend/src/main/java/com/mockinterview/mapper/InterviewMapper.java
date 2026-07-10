package com.mockinterview.mapper;

import com.mockinterview.dto.InterviewResponse;
import com.mockinterview.entity.Interview;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {QuestionMapper.class, MessageMapper.class},
        unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface InterviewMapper {
    @Mapping(source = "id", target = "interviewId")
    @Mapping(source = "score", target = "overallScore")
    @Mapping(source = "interviewType", target = "interviewType")
    @Mapping(source = "interviewType", target = "role")
    @Mapping(source = "adaptedDifficulty", target = "adaptedDifficulty")
    @Mapping(source = "startedAt", target = "createdAt")
    @Mapping(source = "questions", target = "questions")
    @Mapping(source = "messages", target = "messages")
    @Mapping(source = "lastAudio", target = "lastAudio")
    @Mapping(source = "targetQuestionCount", target = "targetQuestions")
    InterviewResponse toDTO(Interview interview);
}

