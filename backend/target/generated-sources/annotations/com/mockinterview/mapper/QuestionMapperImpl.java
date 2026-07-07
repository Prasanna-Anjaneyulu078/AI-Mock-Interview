package com.mockinterview.mapper;

import com.mockinterview.dto.QuestionDTO;
import com.mockinterview.entity.Question;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-07T17:17:40+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Microsoft)"
)
@Component
public class QuestionMapperImpl implements QuestionMapper {

    @Override
    public QuestionDTO toDTO(Question question) {
        if ( question == null ) {
            return null;
        }

        QuestionDTO questionDTO = new QuestionDTO();

        questionDTO.setText( question.getQuestionText() );
        questionDTO.setCodeSnippet( question.getCodeSnippet() );
        questionDTO.setCodeLanguage( question.getCodeLanguage() );
        questionDTO.setCodeType( question.getCodeType() );
        questionDTO.setId( question.getId() );
        questionDTO.setType( question.getType() );
        questionDTO.setIsCodeQuestion( question.getIsCodeQuestion() );

        return questionDTO;
    }
}
