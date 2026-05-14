package com.geointelli.ai.property.service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.AddressSearchRequest;
import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.service.AddressService;
import com.geointelli.ai.property.service.mapper.PropertyMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;
    private final PropertyMapper propertyMapper;

    @GetMapping("/search")
    public ResponseEntity<List<PropertyDTO>> search(AddressSearchRequest req) {
        return ResponseEntity.ok(addressService.search(req).stream().map(propertyMapper::toDTO).toList());
    }
}