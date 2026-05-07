package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.LandDTO;
import com.geointelli.ai.property.service.entity.Land;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface LandMapper {
    LandDTO toDTO(Land land);
    Land toEntity(LandDTO landDTO);
}
