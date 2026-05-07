package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.dto.TaxDTO;
import com.geointelli.ai.property.service.entity.Tax;

@Mapper(componentModel = "spring")
public interface TaxMapper {
    TaxDTO toDTO(Tax tax);
    Tax toEntity(TaxDTO taxDTO);
}
