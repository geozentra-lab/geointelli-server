package com.geointelli.ai.property.service.mapper.external;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.client.dto.AssessmentInfo;
import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.config.StringSanitizer;
import com.geointelli.ai.property.service.dto.AssessmentDTO;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class, uses = StringSanitizer.class)
public interface ExternalAssessmentMapper {
    AssessmentDTO toDTO(AssessmentInfo assessmentInfo);
}
