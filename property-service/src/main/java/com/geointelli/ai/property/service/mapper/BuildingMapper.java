package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.BuildingDTO;
import com.geointelli.ai.property.service.entity.Building;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface BuildingMapper {
    BuildingDTO toDTO(Building building);
    Building toEntity(BuildingDTO buildingDTO);
}
