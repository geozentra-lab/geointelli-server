package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.AssessmentDTO;
import com.geointelli.ai.property.service.entity.Assessment;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface AssessmentMapper {
    AssessmentDTO toDTO(Assessment assessment);
    Assessment toEntity(AssessmentDTO assessmentDTO);
}
