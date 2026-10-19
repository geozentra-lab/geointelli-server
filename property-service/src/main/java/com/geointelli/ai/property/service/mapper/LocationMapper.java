package com.geointelli.ai.property.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.geointelli.ai.property.service.dto.CountyLocationDTO;
import com.geointelli.ai.property.service.dto.StateDTO;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.State;

/** Maps geography records without exposing their property collections. */
@Mapper(componentModel = "spring")
public interface LocationMapper {
    @Mapping(target = "countyId", source = "id")
    @Mapping(target = "countyName", source = "name")
    @Mapping(target = "stateCode", source = "state.code")
    @Mapping(target = "stateName", source = "state.name")
    CountyLocationDTO toDTO(County county);
    StateDTO toDTO(State state);
}
