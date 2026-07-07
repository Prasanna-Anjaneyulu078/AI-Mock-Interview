package com.mockinterview.mapper;

import com.mockinterview.dto.InterviewResponse;
import com.mockinterview.dto.MessageDTO;
import com.mockinterview.dto.QuestionDTO;
import com.mockinterview.entity.Interview;
import com.mockinterview.entity.Message;
import com.mockinterview.entity.Question;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-07T17:17:40+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Microsoft)"
)
@Component
public class InterviewMapperImpl implements InterviewMapper {

    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private MessageMapper messageMapper;

    @Override
    public InterviewResponse toDTO(Interview interview) {
        if ( interview == null ) {
            return null;
        }

        InterviewResponse interviewResponse = new InterviewResponse();

        if ( interview.getId() != null ) {
            interviewResponse.setInterviewId( String.valueOf( interview.getId() ) );
        }
        interviewResponse.setOverallScore( interview.getScore() );
        interviewResponse.setInterviewType( interview.getInterviewType() );
        interviewResponse.setRole( interview.getInterviewType() );
        interviewResponse.setCreatedAt( interview.getStartedAt() );
        interviewResponse.setQuestions( questionListToQuestionDTOList( interview.getQuestions() ) );
        interviewResponse.setMessages( messageListToMessageDTOList( interview.getMessages() ) );
        interviewResponse.setLastAudio( interview.getLastAudio() );
        interviewResponse.setCurrentQuestion( interview.getCurrentQuestion() );
        interviewResponse.setTotalQuestions( interview.getTotalQuestions() );
        interviewResponse.setStatus( interview.getStatus() );
        interviewResponse.setFeedback( interview.getFeedback() );

        return interviewResponse;
    }

    protected List<QuestionDTO> questionListToQuestionDTOList(List<Question> list) {
        if ( list == null ) {
            return null;
        }

        List<QuestionDTO> list1 = new ArrayList<QuestionDTO>( list.size() );
        for ( Question question : list ) {
            list1.add( questionMapper.toDTO( question ) );
        }

        return list1;
    }

    protected List<MessageDTO> messageListToMessageDTOList(List<Message> list) {
        if ( list == null ) {
            return null;
        }

        List<MessageDTO> list1 = new ArrayList<MessageDTO>( list.size() );
        for ( Message message : list ) {
            list1.add( messageMapper.toDTO( message ) );
        }

        return list1;
    }
}
