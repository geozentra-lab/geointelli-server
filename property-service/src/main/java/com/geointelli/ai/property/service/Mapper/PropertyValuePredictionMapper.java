package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.PropertyValuePredictionDTO;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface PropertyValuePredictionMapper {
    PropertyValuePredictionDTO toDTO(PropertyValuePrediction propertyValuePrediction);
    PropertyValuePrediction toEntity(PropertyValuePredictionDTO propertyValuePredictionDTO);
}
