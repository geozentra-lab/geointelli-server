package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.SchoolAnalysisDTO;
import com.geointelli.ai.property.service.entity.SchoolAnalysis;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface SchoolAnalysisMapper {
    SchoolAnalysisDTO toDTO(SchoolAnalysis analysis);
    SchoolAnalysis toEntity(SchoolAnalysisDTO analysisDTO);
}
