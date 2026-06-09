package com.geointelli.ai.property.service.service.impl;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImage;
import com.geointelli.ai.property.service.exception.ImageDownloadException;
import com.geointelli.ai.property.service.repository.PropertyImageRepository;
import com.geointelli.ai.property.service.service.ImageDownloadService;
import com.geointelli.ai.property.service.service.ImageStorageService;
import com.geointelli.ai.property.service.service.PropertyImageService;
import com.geointelli.ai.property.service.util.HashUtil;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PropertyImageServiceImpl implements PropertyImageService {
    private final ImageDownloadService imageDownloadService;
    private final PropertyImageRepository propertyImageRepository;
    private final ImageStorageService imageStorageService;

    public PropertyImageServiceImpl(ImageDownloadService imageDownloadService,
            PropertyImageRepository propertyImageRepository,
            @Qualifier("localStorage") ImageStorageService imageStorageService) {

        this.imageDownloadService = imageDownloadService;
        this.propertyImageRepository = propertyImageRepository;
        this.imageStorageService = imageStorageService;
    }

    @Transactional
    @Override
    public PropertyImage processAndSave(Property property, String imageUrl, boolean primary, int order) {
        try {
            if (imageUrl == null || imageUrl.isBlank()) {
                return null;
            }

            log.info("Processing image {}", imageUrl);
            byte[] imageBytes = imageDownloadService.download(imageUrl);

            log.info("Downloaded image {}", imageUrl);
            String hash = HashUtil.sha256(imageBytes);

            log.info("Hash generated {}", hash);

            Optional<PropertyImage> existing = propertyImageRepository.findByPropertyAndImageHash(property, hash);

            if (existing.isPresent()) {
                log.info("Image already exists {}", hash);
                return existing.get();
            }

            String fileName = property.getId() + "_" + hash + ".jpg";

            log.info("Calling storage service");

            String storagePath = imageStorageService.store(imageBytes, fileName);

            log.info("Stored image at {}", storagePath);

            PropertyImage image = new PropertyImage();

            image.setProperty(property);
            image.setOriginalUrl(imageUrl);
            image.setStoragePath(storagePath);
            image.setImageHash(hash);
            image.setPrimaryImage(primary);
            image.setDisplayOrder(order);

            property.getImages().add(image);

            return propertyImageRepository.save(image);
        } 
        catch (ImageDownloadException e) {
            log.warn("Skipping broken image URL {} for property {}", imageUrl, property.getId());
        }
        return null;
    }

    @Override
    public void processPrimaryImage(Property property, String primaryPhotoUrl) {
        processAndSave(property, primaryPhotoUrl, true ,0);
    }

    @Override
    public void processAltPhotos(Property property, String altPhotos) {
        if (altPhotos == null || altPhotos.isBlank()) {
            return;
        }
        String[] urls = altPhotos.split(",");
        int order = 1;
        for (String url : urls) {
            processAndSave(property, url.trim(), false, order++);
        }
    }
}
