package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.ExtraFeatureDTO;
import com.geointelli.ai.property.service.entity.ExtraFeature;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface ExtraFeatureMapper {
    ExtraFeatureDTO toDTO(ExtraFeature extraFeature);
    ExtraFeature toEntity(ExtraFeatureDTO extraFeatureDTO);
}
