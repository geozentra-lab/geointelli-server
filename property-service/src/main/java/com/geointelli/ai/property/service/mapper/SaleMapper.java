package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.SaleDTO;
import com.geointelli.ai.property.service.entity.Sale;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface SaleMapper {

    SaleDTO toDTO(Sale sale);
    Sale toEntity(SaleDTO saleDTO);
}