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


@RestController
@RequestMapping("/api/properties")
@AllArgsConstructor
public class PropertyController {
    private final PropertyService propertyService;

    @GetMapping("/external/{folio}")
    public ResponseEntity<PropertyDTO> getPropertyByFolioAPI(@PathVariable String folio) {
        PropertyDTO property = propertyService.getByFolioAPI(folio);
        if (property == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(property);
    }

    @GetMapping("/{folio}")
    public ResponseEntity<PropertyDTO> getPropertyByFolio(@PathVariable String folio) {
        PropertyDTO property = propertyService.getByFolio(folio);
        return ResponseEntity.ok(property);
    }
    
}
