package com.geointelli.ai.property.service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.ParcelDTO;
import com.geointelli.ai.property.service.service.ParcelService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/parcels")
@AllArgsConstructor
public class ParcelController {
    private final ParcelService parcelService;

    @GetMapping
    public ResponseEntity<List<ParcelDTO>> getParcels(@RequestParam String bbox) {

        String[] coords = bbox.split(",");

        double xmin = Double.parseDouble(coords[0]);
        double ymin = Double.parseDouble(coords[1]);
        double xmax = Double.parseDouble(coords[2]);
        double ymax = Double.parseDouble(coords[3]);

        List<ParcelDTO> parcels = parcelService.getParcelsWithinBoundingBox(xmin, ymin, xmax, ymax);

        return ResponseEntity.ok(parcels);
    }

    @GetMapping("/byfolio")
    ResponseEntity<List<ParcelDTO>> getByFolio(@RequestParam String folio){
        return ResponseEntity.ok(parcelService.getByFolio(folio));
    }
}
