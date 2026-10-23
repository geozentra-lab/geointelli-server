package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.dto.ChildPropertyDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
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

    /**
     * Returns a page of the child properties of the given parent, ordered by ID ascending.
     *
     * @param parentId internal ID of the parent property; must be positive
     * @param page     zero-based page index
     * @param size     page size (1–100)
     * @return the requested page of children; an empty page if the parent has no children
     * @throws IllegalArgumentException if {@code parentId} is null or not positive
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException
     *         if no property exists with the given ID
     */
    PageResponse<ChildPropertyDTO> getChildPropertiesByParentId(Long parentId, int page, int size);
}
