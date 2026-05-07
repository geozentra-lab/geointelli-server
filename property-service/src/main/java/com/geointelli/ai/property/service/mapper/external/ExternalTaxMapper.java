package com.geointelli.ai.property.service.mapper.external;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.client.dto.TaxableInfo;
import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.config.StringSanitizer;
import com.geointelli.ai.property.service.dto.TaxDTO;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class, uses = StringSanitizer.class)
public interface ExternalTaxMapper {
    TaxDTO toDTO(TaxableInfo taxableInfo);
}
