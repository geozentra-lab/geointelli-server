package com.geointelli.ai.property.service.mapper.external;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.client.dto.ExtraFeatureInfo;
import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.config.StringSanitizer;
import com.geointelli.ai.property.service.dto.ExtraFeatureDTO;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class, uses = StringSanitizer.class)
public interface ExternalExtraFeatureMapper {
    ExtraFeatureDTO toDTO(ExtraFeatureInfo extraFeatureInfo);
}
