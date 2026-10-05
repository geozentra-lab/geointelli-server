package com.geointelli.ai.property.service.controller;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.service.CsvImportService;
import com.geointelli.ai.property.service.service.CsvFolderImportService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/ingestion")
@AllArgsConstructor
public class CsvImportController {
    private final CsvImportService csvImportService;
    private final CsvFolderImportService csvFolderImportService;

    @PostMapping("/import/folder")
    public ResponseEntity<String> importFolder(@RequestParam String folderPath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvFolderImportService.importFolder(Path.of(folderPath), county, sourceState);
        return ResponseEntity.ok("CSV folder import completed");
    }

    @PostMapping("/import/property-images")
    public ResponseEntity<String> importPropertyImages(@RequestParam String filePath, @RequestParam String county,
            @RequestParam String sourceState) throws IOException {
        csvImportService.importPropertyImages(Path.of(filePath), county, sourceState);
        return ResponseEntity.ok("Image CSV import completed");
    }

    @PostMapping("/import/properties")
    public ResponseEntity<String> importCsv(@RequestParam String filePath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvImportService.importProperties(Path.of(filePath), county, sourceState);
        return ResponseEntity.ok("CSV import completed");
    }

    @PostMapping("/import/addresses")
    public ResponseEntity<String> importAddresses(@RequestParam String filePath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvImportService.importAddresses(Path.of(filePath), county, sourceState);
        return ResponseEntity.ok("CSV import completed");
    }

    @PostMapping("/import/assessments")
    public ResponseEntity<String> importAssessments(@RequestParam String filePath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvImportService.importAssessments(Path.of(filePath), county, sourceState);
        return ResponseEntity.ok("CSV import completed");
    }

    @PostMapping("/import/buildings")
    public ResponseEntity<String> importBuildings(@RequestParam String filePath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvImportService.importBuildings(Path.of(filePath), county, sourceState);
        return ResponseEntity.ok("CSV import completed");
    }

    @PostMapping("/import/owners")
    public ResponseEntity<String> importOwners(@RequestParam String ownersCsvPath, @RequestParam String propertyOwnersCsvPath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvImportService.importOwners(Path.of(ownersCsvPath), Path.of(propertyOwnersCsvPath), county, sourceState);
        return ResponseEntity.ok("CSV import completed");
    }

    @PostMapping("/import/lands")
    public ResponseEntity<String> importLands(@RequestParam String filePath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvImportService.importLands(Path.of(filePath), county, sourceState);
        return ResponseEntity.ok("CSV import completed");
    }

    @PostMapping("/import/sales")
    public ResponseEntity<String> importSales(@RequestParam String filePath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvImportService.importSales(Path.of(filePath), county, sourceState);
        return ResponseEntity.ok("CSV import completed");
    }

    @PostMapping("/import/taxes")
    public ResponseEntity<String> importTaxes(@RequestParam String filePath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvImportService.importTaxes(Path.of(filePath), county, sourceState);
        return ResponseEntity.ok("CSV import completed");
    }

    @PostMapping("/import/extra-features")
    public ResponseEntity<String> importExtraFeatures(@RequestParam String filePath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvImportService.importExtraFeatures(Path.of(filePath), county, sourceState);
        return ResponseEntity.ok("CSV import completed");
    }

    @PostMapping("/import/parcels")
    public ResponseEntity<String> importParcels(@RequestParam String filePath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvImportService.importParcels(Path.of(filePath), county, sourceState);
        return ResponseEntity.ok("CSV import completed");
    }

    @PostMapping("/import/property-value-predictions")
    public ResponseEntity<String> importPropertyValuePredictions(@RequestParam String filePath, @RequestParam String county, @RequestParam String sourceState) throws IOException {
        csvImportService.importPropertyValuePredictions(Path.of(filePath), county, sourceState);
        return ResponseEntity.ok("CSV import completed");
    }
}
