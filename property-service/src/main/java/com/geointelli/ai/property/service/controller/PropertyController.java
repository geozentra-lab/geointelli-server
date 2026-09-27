package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.mapper.PropertyMapper;
import com.geointelli.ai.property.service.service.PropertyService;

import lombok.AllArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import jakarta.validation.Valid;
import com.geointelli.ai.property.service.dto.PropertySearchRequest;
import com.geointelli.ai.property.service.dto.PropertySearchResponse;
import com.geointelli.ai.property.service.service.PropertySearchService;


@RestController
@RequestMapping("/api/properties")
@AllArgsConstructor
public class PropertyController {
    private final PropertyService propertyService;
    private final PropertySearchService propertySearchService;

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
    
}
