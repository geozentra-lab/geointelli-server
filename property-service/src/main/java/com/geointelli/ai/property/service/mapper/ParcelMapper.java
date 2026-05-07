package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.ParcelDTO;
import com.geointelli.ai.property.service.entity.Parcel;
@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class, uses = GeometryMapper.class)
public interface ParcelMapper {
    @Mapping(target = "geom", source = "geom", qualifiedByName = "toDTO")
    ParcelDTO toDTO(Parcel parcel);

    @Mapping(target = "geom", source = "geom", qualifiedByName = "fromDTO")
    Parcel toEntity(ParcelDTO dto);
}