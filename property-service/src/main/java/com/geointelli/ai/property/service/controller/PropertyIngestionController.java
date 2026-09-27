package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.service.CountyService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.manager.PropertyIngestionManager;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.AddressService;
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
    private final AddressService addressService;
    private final PropertyRepository propertyRepository;
    private final PropertyIngestionService propertyIngestionService;
    private final CountyService countyService;

    @PostMapping("/run")
    public ResponseEntity<String> runIngestion() {
        log.info("Manual ingestion triggered via API");
        List<String> folios = parcelService.getAllFolios(countyService.miamiDade().getId());
        Set<String> foliosSet = new HashSet<>(propertyService.getAllFolios(countyService.miamiDade().getId()));
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
        // propertyIngestionManager.ingestAllFolios(parcelService.getAllFolios(countyService.miamiDade().getId()));
        // propertyIngestionManager.ingestAllBuildings(propertyService.getAllFolios(countyService.miamiDade().getId()));
        // List<String> folios = parcelService.getAllFolios(countyService.miamiDade().getId());
        // for(String folio : folios){
        //     propertyIngestionService.ingest(folio);
        // }
        return ResponseEntity.ok("Property ingestion started");
    }

    @PostMapping("/ingestbuildings")
    public ResponseEntity<String> runBuildingsIngestion() {
        log.info("Buildings ingestion triggered via API");
        propertyIngestionManager.ingestAllBuildings(propertyService.getAllFolios(countyService.miamiDade().getId()));
        return ResponseEntity.ok("Buildings ingestion started");
    }

    @PostMapping("/ingest_extrafeatures")
    public ResponseEntity<String> runExtraFeaturesIngestion() {
        log.info("Extra features ingestion triggered via API");
        List<String> folios = propertyService.getAllFoliosForPropertyWithoutExtraFeatures(countyService.miamiDade().getId());
        // List<String> folios = new ArrayList<>(List.of(
        //     "3040310170020",
        //     "3059100050100",
        //     "3059100050110",
        //     "3059100042800",
        //     "3059100050120",
        //     "3059100060480",
        //     "3059100110020",
        //     "3059100110120",
        //     "3059100110310",
        //     "3059100110370",
        //     "3059100110560",
        //     "3059100110620",
        //     "3059100110750",
        //     "3059100110240",
        //     "3059100110010",
        //     "3530070031460"
        // ));
        log.info("count of folios without extra features {}", folios.size());
        propertyIngestionManager.ingestAllExtraFeatures(folios);
        return ResponseEntity.ok("Extra features ingestion started");
    }

    @PostMapping("/ingest_sales")
    public ResponseEntity<String> runSalesIngestion() {
        log.info("Sales ingestion triggered via API");
        propertyIngestionManager.ingestAllSales(propertyRepository.findFoliosWithoutSales(countyService.miamiDade().getId()));
        return ResponseEntity.ok("Sales ingestion started");
    }

    @PostMapping("/ingestaddresses")
    public ResponseEntity<String> runAddressesIngestion() {
        // List<Long> allPropertiesId = propertyService.getAllIds();
        // Set<Long> currentAddressesPropertyId = new HashSet<>(addressService.getAllPropertiesId());
        // List<Long> nonExistingIds = allPropertiesId.stream().filter(propertyId -> !currentAddressesPropertyId.contains(propertyId))
        //                                             .toList();
        // List<String> nonExistingFolios = nonExistingIds.stream().map(propertyRepository::findById)
        //                                                     .flatMap(Optional::stream).map(Property::getFolio).collect(Collectors.toList());        
        List<String> nonExistingFolios = propertyRepository.findFoliosWithoutAddress(countyService.miamiDade().getId());
        log.info("Addresses ingestion triggered via API");
        propertyIngestionManager.ingestAllAddresses(nonExistingFolios);
        return ResponseEntity.ok("Addresses ingestion started");
    }
}