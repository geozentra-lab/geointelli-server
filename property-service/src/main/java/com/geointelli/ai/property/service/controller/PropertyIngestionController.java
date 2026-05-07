package com.geointelli.ai.property.service.controller;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.manager.PropertyIngestionManager;
import com.geointelli.ai.property.service.service.ParcelService;
import com.geointelli.ai.property.service.service.PropertyIngestionService;
import com.geointelli.ai.property.service.service.PropertyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ingestion")
@Slf4j
public class PropertyIngestionController {

    private final PropertyIngestionManager propertyIngestionManager;
    private final ParcelService parcelService;
    private final PropertyService propertyService;
    private final PropertyIngestionService propertyIngestionService;

    @PostMapping("/run")
    public ResponseEntity<String> runIngestion() {
        log.info("Manual ingestion triggered via API");
        List<String> folios = parcelService.getAllFolios();
        Set<String> foliosSet = new HashSet<>(propertyService.getAllFolios());
        List<String> nonExistingFolios = new ArrayList<>();
        for(String folio: folios){
            if (!foliosSet.contains(folio)) {
                nonExistingFolios.add(folio);
            }
        }
        log.info("Filtering is done");
        System.out.println("All Folios length: "+ folios.size());
        System.out.println("Existing Folios length: "+ foliosSet.size());
        System.out.println("Non Existing Folios length: "+ nonExistingFolios.size());

        propertyIngestionManager.ingestAllFolios(nonExistingFolios);
        // propertyIngestionManager.ingestAllFolios(parcelService.getAllFolios());
        // propertyIngestionManager.ingestAllBuildings(propertyService.getAllFolios());
        // List<String> folios = parcelService.getAllFolios();
        // for(String folio : folios){
        //     propertyIngestionService.ingest(folio);
        // }
        return ResponseEntity.ok("Property ingestion started");
    }
}