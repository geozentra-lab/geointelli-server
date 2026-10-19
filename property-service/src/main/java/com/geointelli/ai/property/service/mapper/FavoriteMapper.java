package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.FavoriteDTO;
import com.geointelli.ai.property.service.entity.Favorite;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface FavoriteMapper {
    @Mapping(target = "propertyId", source = "favorite.property.id")
    FavoriteDTO toDTO(Favorite favorite);
    Favorite toEntity(FavoriteDTO favoriteDTO);
}
