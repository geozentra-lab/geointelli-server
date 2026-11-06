package com.geointelli.ai.property.service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.AddressSearchRequest;
import com.geointelli.ai.property.service.dto.PropertySearchItem;
import com.geointelli.ai.property.service.service.AddressService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/search")
    public ResponseEntity<List<PropertySearchItem>> search(AddressSearchRequest req) {
        return ResponseEntity.ok(addressService.search(req));
    }

    @GetMapping("/suggest")
    public ResponseEntity<List<String>> suggest(@RequestParam String raw) {
        return ResponseEntity.ok(addressService.suggest(raw));
    }
}