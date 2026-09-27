package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapping;

import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy; 

import com.geointelli.ai.property.service.config.IgnoreUnmappedMapperConfig;
import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.entity.Property;

@Mapper(
    componentModel = "spring",
    collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED, 
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

    @Mapping(target = "countyId", source = "county.id")
    @Mapping(target = "countyName", source = "county.name")
    @Mapping(target = "stateId", source = "county.state.id")
    @Mapping(target = "stateCode", source = "county.state.code")
    @Mapping(target = "stateName", source = "county.state.name")
    @Mapping(target = "parentPropertyId", source = "parentProperty.id")
    PropertyDTO toDTO(Property property);
    
    @Mapping(target = "county", ignore = true)
    @Mapping(target = "parentProperty", ignore = true)
    Property toEntity(PropertyDTO propertyDTO);
}
