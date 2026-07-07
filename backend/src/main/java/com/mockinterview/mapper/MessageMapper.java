package com.mockinterview.mapper;

import com.mockinterview.dto.MessageDTO;
import com.mockinterview.entity.Message;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface MessageMapper {
    MessageDTO toDTO(Message message);
}
