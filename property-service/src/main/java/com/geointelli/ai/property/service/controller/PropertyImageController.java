package com.geointelli.ai.property.service.controller;

import java.nio.file.Path;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.PropertyImageDTO;
import com.geointelli.ai.property.service.service.PropertyImageImportService;
import com.geointelli.ai.property.service.service.PropertyImageService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/images")
@AllArgsConstructor
public class PropertyImageController {
    private final PropertyImageImportService propertyImageImportService; 
    private final PropertyImageService propertyImageService; 

    @PostMapping("/import/csv") 
    public ResponseEntity<String> importCsv( @RequestParam String filePath) {
        propertyImageImportService.importCsv(Path.of(filePath));
        return ResponseEntity.ok( "CSV import completed");
    }

    @PostMapping("/import/folder") 
    public ResponseEntity<String> importFolder( @RequestParam String folderPath) { 
        propertyImageImportService.importFolder(Path.of(folderPath));
        return ResponseEntity.accepted().body("Import started in background");
    }

    @PostMapping("/import/zip/{zip}")
    public ResponseEntity<String> importZip(@PathVariable String zip) {
        Path csv = Path.of("miami_zips_data_image", zip + ".csv");
        propertyImageImportService.importCsv(csv);
        return ResponseEntity.ok(
                "Import completed for zip " + zip);
    }

    @GetMapping("/property/{propertyId}")
    public List<PropertyImageDTO> getImages(@PathVariable Long propertyId) {

        return propertyImageService.getImages(propertyId);
    }
}
