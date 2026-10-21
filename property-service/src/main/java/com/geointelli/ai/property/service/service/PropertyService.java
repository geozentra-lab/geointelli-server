package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.entity.Property;

public interface PropertyService {
    Property saveProperty(Property property);
    PropertyDTO getByFolioAPI(String folio);
    PropertyDTO getByFolio(String folio, Long countyId);
    List<String> getAllFolios(Long countyId);
    List<Long> getAllIds();
    List<String> getAllFoliosForPropertyWithoutExtraFeatures(Long countyId);

    /**
     * Retrieves a property by its internal database identifier.
     * <p>
     * Works for both parent and child properties, including properties whose
     * folio is {@code null}. The returned DTO includes county/state details and
     * the parent property ID when one exists.
     *
     * @param id the internal property ID; must be a positive number
     * @return the property details
     * @throws IllegalArgumentException if {@code id} is null or not positive
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException
     *         if no property exists with the given ID
     */
    PropertyDTO getPropertyById(Long id);
}
