package com.mockinterview.mapper;

import com.mockinterview.dto.QuestionDTO;
import com.mockinterview.entity.Question;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface QuestionMapper {
    @Mapping(source = "questionText", target = "text")
    QuestionDTO toDTO(Question question);
}
