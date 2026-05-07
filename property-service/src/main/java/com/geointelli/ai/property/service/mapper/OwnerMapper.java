package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.OwnerDTO;
import com.geointelli.ai.property.service.entity.Owner;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface OwnerMapper {
    OwnerDTO toDTO(Owner owner);
    Owner toEntity(OwnerDTO ownerDTO);
}
