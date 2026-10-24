package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.mapper.PropertyMapper;
import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.service.PropertyService;

import lombok.AllArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.validation.Valid;
import com.geointelli.ai.property.service.service.PropertySearchService;
import com.geointelli.ai.property.service.dto.PropertyExtraFeaturesDTO;
import com.geointelli.ai.property.service.service.ExtraFeatureService;


@RestController
@RequestMapping("/api/properties")
@AllArgsConstructor
public class PropertyController {
    private final PropertyService propertyService;
    private final PropertySearchService propertySearchService;
    private final ExtraFeatureService extraFeatureService;

    @GetMapping("/search")
    public ResponseEntity<PropertySearchResponse> search(@Valid @ModelAttribute PropertySearchRequest request) {
        return ResponseEntity.ok(propertySearchService.search(request));
    }

    @GetMapping("/external/{folio}")
    public ResponseEntity<PropertyDTO> getPropertyByFolioAPI(@PathVariable String folio) {
        PropertyDTO property = propertyService.getByFolioAPI(folio);
        if (property == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(property);
    }

    @GetMapping("/{folio}")
    public ResponseEntity<PropertyDTO> getPropertyByFolio(@PathVariable String folio, @RequestParam Long countyId) {
        PropertyDTO property = propertyService.getByFolio(folio, countyId);
        return ResponseEntity.ok(property);
    }
  
    @GetMapping("/id/{id}/extra-features")
    public ResponseEntity<PropertyExtraFeaturesDTO> getExtraFeatures(
            @PathVariable Long id,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "false") boolean history,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(extraFeatureService.getExtraFeatures(id, year, history, page, size));
    }

    /**
     * Returns property details by internal ID.
     *
     * @param id internal property ID
     * @return 200 with the property; 400 if the ID is invalid; 404 if not found
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<PropertyDTO> getPropertyById(@PathVariable Long id) {
        PropertyDTO property = propertyService.getPropertyById(id);
        return ResponseEntity.ok(property);
    }

    /**
     * Returns a paginated list of the child properties of a parent property, ordered by ID.
     *
     * @param id        internal ID of the parent property
     * @param pageQuery page (default 0) and size (default 20, max 100)
     * @return 200 with a page of children (empty if the parent has none);
     *         400 if the ID or paging parameters are invalid; 404 if the parent does not exist
     */
    @GetMapping("/id/{id}/children")
    public ResponseEntity<PageResponse<ChildPropertyDTO>> getChildProperties(@PathVariable Long id, @Valid @ModelAttribute PageQuery pageQuery) {
        PageResponse<ChildPropertyDTO> children =
                propertyService.getChildPropertiesByParentId(id, pageQuery.getPage(), pageQuery.getSize());
        return ResponseEntity.ok(children);
    }
}
