package com.mockinterview.mapper;

import com.mockinterview.dto.QuestionDTO;
import com.mockinterview.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface QuestionMapper {
    @Mapping(source = "questionText", target = "text")
    @Mapping(source = "codeSnippet", target = "codeSnippet")
    @Mapping(source = "codeLanguage", target = "codeLanguage")
    @Mapping(source = "codeType", target = "codeType")
    QuestionDTO toDTO(Question question);
}

