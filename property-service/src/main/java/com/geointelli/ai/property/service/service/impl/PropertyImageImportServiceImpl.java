package com.geointelli.ai.property.service.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImage;
import com.geointelli.ai.property.service.repository.PropertyImageRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.PropertyImageImportService;
import com.geointelli.ai.property.service.service.PropertyImageService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyImageImportServiceImpl implements PropertyImageImportService {
    private final PropertyRepository propertyRepository;
    private final PropertyImageService propertyImageService;
    private final PropertyImageRepository propertyImageRepository;
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(10);
    private final EntityManager entityManager;

    @Autowired
    @Lazy
    private PropertyImageImportService self;

    @Async("importExecutor")
    @Override
    public void importCsv(Path csvFile) {
        log.info("Importing images from {}", csvFile);

        log.info("Loading properties...");
        List<Property> properties = propertyRepository.findAllWithAddress();
        log.info("Loaded {} properties", properties.size());

        log.info("Loading existing images...");
        List<PropertyImage> allImages = propertyImageRepository.findAll();
        log.info("Loaded {} images", allImages.size());

        log.info("Associating images to properties...");
        Map<Long, List<PropertyImage>> imagesByPropertyId = allImages.stream()
                .collect(Collectors.groupingBy(img -> img.getProperty().getId()));

        properties.forEach(p -> {
            List<PropertyImage> images = imagesByPropertyId.getOrDefault(p.getId(), Collections.emptyList());
            p.setImages(new ArrayList<>(images));
        });

        entityManager.clear();

        Map<String, Property> propertyMap = properties.stream()
            .filter(p -> p.getAddress() != null)
            .collect(Collectors.toMap(
                p -> (p.getAddress().getZip() + "|" + p.getAddress().getAddress()).toLowerCase().trim(),
                p -> p,
                (a, b) -> a
            ));

        // after building propertyMap, find and log that specific address
        propertyMap.keySet().stream()
            .filter(k -> k.contains("10400"))
            .limit(3)
            .forEach(k -> log.info("Map key: '{}'", k));

        // System.exit(0);

        List<Future<?>> futures = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvFile);
            CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).get().parse(reader)) {

            for (CSVRecord record : parser) {
                final CSVRecord r = record;
                futures.add(imageExecutor.submit(() -> processRecord(r, propertyMap)));
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
            // String key = (zipCode + "-0000|" + address + "-0000").toLowerCase().trim();

            String normalizedZip = zipCode.contains("-") ? zipCode : zipCode + "-0000";
            String normalizedAddress = address.replace(zipCode, normalizedZip); // replace bare zip inside address
            String key = (normalizedZip + "|" + normalizedAddress).toLowerCase().trim();

            log.info("RAW csv address: '{}'", record.get("formatted_address"));
            log.info("RAW csv zip: '{}'", record.get("zip_code"));
            log.info("Built key: '{}'", key);
            // System.exit(0);

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

    @Async("importExecutor")
    @Override
    public void importFolder(Path folder) {
        log.info("Importing images from folder: {}", folder);
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(path -> path.toString().toLowerCase().endsWith(".csv"))
                 .forEach(csv -> self.importCsv(csv)); // use self, not this
        } catch (IOException e) {
            throw new RuntimeException("Failed to read folder: " + folder, e);
        }
    }
}
