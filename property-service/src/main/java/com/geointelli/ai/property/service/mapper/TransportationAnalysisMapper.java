package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.TransportationAnalysisDTO;
import com.geointelli.ai.property.service.entity.TransportationAnalysis;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface TransportationAnalysisMapper {
    TransportationAnalysisDTO toDTO(TransportationAnalysis analysis);
    TransportationAnalysis toEntity(TransportationAnalysisDTO analysisDTO);
}
