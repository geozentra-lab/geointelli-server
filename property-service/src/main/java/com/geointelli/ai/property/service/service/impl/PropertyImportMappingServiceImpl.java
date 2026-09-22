package com.geointelli.ai.property.service.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImportMapping;
import com.geointelli.ai.property.service.repository.PropertyImportMappingRepository;
import com.geointelli.ai.property.service.service.PropertyImportMappingService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service 
@Slf4j 
@AllArgsConstructor 
public class PropertyImportMappingServiceImpl implements PropertyImportMappingService {
    private PropertyImportMappingRepository propertyImportMappingRepository;

	@Override
	public Property getGeoZentraProperty(String county, String state, Long sourcePropertyId) {
		return propertyImportMappingRepository.findBySourceCountyAndSourceStateAndSourcePropertyId(
                        county,
                        state,
                        sourcePropertyId).map(PropertyImportMapping::getGeozentraProperty).orElseThrow(() ->
                                new IllegalArgumentException("No GeoZentra property mapping found for "
                                        + county + " / "
                                        + state + " / "
                                        + sourcePropertyId
                        )
                );
	}

	@Override
	public PropertyImportMapping saveMapping(String county, String state, Long sourcePropertyId, Property property) {
		PropertyImportMapping mapping = new PropertyImportMapping();
                mapping.setSourceCounty(county);
                mapping.setSourceState(state);
                mapping.setSourcePropertyId(sourcePropertyId);
                mapping.setGeozentraProperty(property);
                mapping.setCreatedAt(LocalDateTime.now());
                return propertyImportMappingRepository.save(mapping);
	}

        private Property getMappedProperty(String sourceCounty, String sourceState, Long sourcePropertyId) {
                PropertyImportMapping mapping = propertyImportMappingRepository
                        .findBySourceCountyAndSourceStateAndSourcePropertyId(sourceCounty, sourceState,
                                sourcePropertyId).orElseThrow(() ->
                                new IllegalStateException("No property mapping found for source property ID: "
                                        + sourcePropertyId + " in " + sourceCounty + ", " + sourceState));

                return mapping.getGeozentraProperty();
        }
    
}
