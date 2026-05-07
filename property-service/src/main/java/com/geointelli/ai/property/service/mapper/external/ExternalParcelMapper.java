package com.geointelli.ai.property.service.mapper.external;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.config.StringSanitizer;
import com.geointelli.ai.property.service.dto.ParcelDTO;
import com.geointelli.ai.property.service.entity.Parcel;
@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class, uses = StringSanitizer.class)
public interface ExternalParcelMapper {
    ParcelDTO toDTO(Parcel parcel);
}