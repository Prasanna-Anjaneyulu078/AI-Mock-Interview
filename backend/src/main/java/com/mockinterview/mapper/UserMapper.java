package com.mockinterview.mapper;

import com.mockinterview.dto.UserDTO;
import com.mockinterview.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(source = "fullName", target = "fullName")
    @Mapping(source = "profileImage", target = "profileImage")
    UserDTO toDTO(User user);
}
