package com.geointelli.ai.property.service.mapper;

import com.geointelli.ai.property.service.dto.ScenarioDTO;
import com.geointelli.ai.property.service.entity.Scenario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps a saved scenario to its API response; the linked property is exposed by ID only (SCRUM-147). */
@Mapper(componentModel = "spring")
public interface ScenarioMapper {
    @Mapping(target = "propertyId", source = "property.id")
    ScenarioDTO toDTO(Scenario scenario);
}
