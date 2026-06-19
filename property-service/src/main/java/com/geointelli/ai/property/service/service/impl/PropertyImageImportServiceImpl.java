package com.geointelli.ai.property.service.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
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
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(10);

    @Override
    public void importCsv(Path csvFile) {
        log.info("Importing images from {}", csvFile);

        Map<String, Property> propertyMap = propertyRepository
            .findAllWithImagesAndAddress()
            .stream()
            .collect(Collectors.toMap(
                p -> (p.getAddress().getZip() + "|" + p.getAddress().getAddress()).toLowerCase().trim(),
                p -> p
            ));

        log.info("\'"+
            propertyMap.keySet()
                    .stream()
                    .limit(10)
                    .toList()
                    .toString()+"\'"
            );

        // System.exit(0);

        List<Future<?>> futures = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvFile);
            CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).get().parse(reader)) {

            for (CSVRecord record : parser) {
                futures.add(imageExecutor.submit(() -> processRecord(record, propertyMap)));
            }
            for (Future<?> future : futures) {
                try { future.get(); } catch (Exception e) { log.error("Task failed", e); }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to import image CSV: " + csvFile, e);
        }
    }

    private void processRecord(CSVRecord record, Map<String, Property> propertyMap) {
        try {
            String address = record.get("formatted_address");
            String zipCode = record.get("zip_code");
            String key = (zipCode + "-0000|" + address + "-0000").toLowerCase().trim();

            log.info("csv:" + key);

            Property property = propertyMap.get(key);
            if (property == null) {
                log.warn("Property not found. Address={}, Zip={}", address, zipCode);
                return;
            }
            propertyImageService.processPrimaryImage(property, record.get("primary_photo"));
            propertyImageService.processAltPhotos(property, record.get("alt_photos"));
        } catch (Exception e) {
            log.error("Failed processing record {}", record.getRecordNumber(), e);
        }
    }

    @Override
    public void importFolder(Path folder) {
        log.info("importing images from folder : " + folder);
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(path -> path.toString()
                                    .toLowerCase()
                                    .endsWith(".csv"))
                                    .forEach(this::importCsv);
        } 
        catch (IOException e) {
            throw new RuntimeException("failed to read folder: " + folder, e);
        }
    }
}
