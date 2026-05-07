package com.geointelli.ai.property.service.mapper;

import org.mapstruct.CollectionMappingStrategy; // Import this
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy; // Import this

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.entity.Property;

@Mapper(
    componentModel = "spring",
    // 1. Tell MapStruct to use your addParcel() method instead of setParcels()
    collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED, 
    // 2. Ensure it doesn't try to map null collections from the DTO
    nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
    uses = {
        OwnerMapper.class,
        AddressMapper.class,
        AssessmentMapper.class,
        BuildingMapper.class,
        LandMapper.class,
        SaleMapper.class,
        TaxMapper.class,
        ParcelMapper.class
    }, 
    config = IgnoreUnmappedMapperConfig.class
)
public interface PropertyMapper {

    PropertyDTO toDTO(Property property);
    Property toEntity(PropertyDTO propertyDTO);
}