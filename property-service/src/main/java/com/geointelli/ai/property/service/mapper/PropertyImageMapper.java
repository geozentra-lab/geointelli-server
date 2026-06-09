package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.PropertyImageDTO;
import com.geointelli.ai.property.service.entity.PropertyImage;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface PropertyImageMapper {
    PropertyImageDTO toDTO(PropertyImage propertyImage);
    PropertyImage toEntity(PropertyImageDTO propertyImageDTO);
} 

