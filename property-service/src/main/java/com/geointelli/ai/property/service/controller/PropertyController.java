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
    private final PropertyMapper propertyMapper;

    @GetMapping("/{folio}")
    public ResponseEntity<PropertyDTO> getPropertyById(@PathVariable String folio) {
        Property property = propertyService.getByFolio(folio);
        if (property == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(propertyMapper.toDTO(property));
    }
    
}
