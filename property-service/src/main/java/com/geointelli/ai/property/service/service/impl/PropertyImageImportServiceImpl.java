package com.geointelli.ai.property.service.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.PropertyImageImportService;
import com.geointelli.ai.property.service.service.PropertyImageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyImageImportServiceImpl implements PropertyImageImportService {
    private final PropertyRepository propertyRepository;
    private final PropertyImageService propertyImageService;

    @Override
    public void importCsv(Path csvFile) {
        log.info("Importing images from {}", csvFile);

        try (BufferedReader reader = Files.newBufferedReader(csvFile);
             CSVParser parser = CSVFormat.DEFAULT
                                .builder()
                                .setHeader()
                                .setSkipHeaderRecord(true)
                                .get()
                                .parse(reader)) {

            for (CSVRecord record : parser) {
                processRecord(record);
            }
        } 
        catch (IOException e) {
            throw new RuntimeException("Failed to import image CSV: " + csvFile, e);
        }
    }

    @Override
    public void importFolder(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(path -> path.toString()
                                    .toLowerCase()
                                    .endsWith(".csv"))
                                    .forEach(this::importCsv);
        } 
        catch (IOException e) {
            throw new RuntimeException("Failed to read folder: " + folder, e);
        }
    }

    private void processRecord(CSVRecord record) {
        try {
            String address = record.get("formatted_address");
            String zipCode = record.get("zip_code");
            String primaryPhoto = record.get("primary_photo");
            String altPhotos = record.get("alt_photos");

            Optional<Property> propertyOpt = propertyRepository.findByAddress_ZipAndAddress_Address(zipCode, address);

            if (propertyOpt.isEmpty()) {
                log.warn("Property not found. Address={}, Zip={}", address, zipCode);
                return;
            }

            Property property = propertyOpt.get();

            propertyImageService.processPrimaryImage(property, primaryPhoto);
            propertyImageService.processAltPhotos(property, altPhotos);

        } 
        catch (Exception e) {
            log.error("Failed processing record {}", record.getRecordNumber(), e);
        }
    }
    
}
