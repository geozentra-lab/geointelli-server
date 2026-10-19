package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.UserDTO;
import com.geointelli.ai.property.service.entity.User;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface UserMapper {
    UserDTO toDTO(User user);
    User toEntity(UserDTO userDTO);
}
