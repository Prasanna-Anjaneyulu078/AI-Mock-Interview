package com.mockinterview.mapper;

import com.mockinterview.dto.MessageDTO;
import com.mockinterview.entity.Message;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-07-07T17:17:40+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.11 (Microsoft)"
)
@Component
public class MessageMapperImpl implements MessageMapper {

    @Override
    public MessageDTO toDTO(Message message) {
        if ( message == null ) {
            return null;
        }

        MessageDTO messageDTO = new MessageDTO();

        messageDTO.setId( message.getId() );
        messageDTO.setRole( message.getRole() );
        messageDTO.setContent( message.getContent() );
        messageDTO.setCreatedAt( message.getCreatedAt() );

        return messageDTO;
    }
}
