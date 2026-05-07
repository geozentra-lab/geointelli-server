package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.AddressDTO;
import com.geointelli.ai.property.service.entity.Address;

@Mapper(componentModel = "spring", config = IgnoreUnmappedMapperConfig.class)
public interface AddressMapper {
    AddressDTO toDTO(Address address);
    Address toEntity(AddressDTO addressDTO);
}
