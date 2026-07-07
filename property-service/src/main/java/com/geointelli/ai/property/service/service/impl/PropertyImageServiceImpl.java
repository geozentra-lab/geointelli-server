package com.geointelli.ai.property.service.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.dto.PropertyImageDTO;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImage;
import com.geointelli.ai.property.service.exception.ImageDownloadException;
import com.geointelli.ai.property.service.mapper.PropertyImageMapper;
import com.geointelli.ai.property.service.repository.PropertyImageRepository;
import com.geointelli.ai.property.service.service.ImageDownloadService;
import com.geointelli.ai.property.service.service.ImageStorageService;
import com.geointelli.ai.property.service.service.PropertyImageService;
import com.geointelli.ai.property.service.service.S3Service;
import com.geointelli.ai.property.service.util.HashUtil;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PropertyImageServiceImpl implements PropertyImageService {
    private final ImageDownloadService imageDownloadService;
    private final PropertyImageRepository propertyImageRepository;
    private final ImageStorageService imageStorageService;
    private final PropertyImageMapper propertyImageMapper;
    private final S3Service s3Service;

    public PropertyImageServiceImpl(ImageDownloadService imageDownloadService,
            PropertyImageRepository propertyImageRepository,
            @Qualifier("localStorage") ImageStorageService imageStorageService,
            PropertyImageMapper propertyImageMapper, S3Service s3Service) {

        this.imageDownloadService = imageDownloadService;
        this.propertyImageRepository = propertyImageRepository;
        this.imageStorageService = imageStorageService;
        this.propertyImageMapper = propertyImageMapper;
        this.s3Service = s3Service;
    }

    @Transactional
    @Override
    public PropertyImage processAndSave(Property property,
                                     String imageUrl, boolean primary, int order) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        try {
            boolean urlAlreadyProcessed = property.getImages().stream()
                    .anyMatch(img -> imageUrl.equals(img.getOriginalUrl()));
            if (urlAlreadyProcessed) {
                log.info("Image URL already processed {}", imageUrl);
                return null;
            }

            byte[] imageBytes = imageDownloadService.download(imageUrl);
            String hash = HashUtil.sha256(imageBytes);

            // if (existingHashes.contains(hash)) {
            //     log.info("Image already exists {}", hash);
            //     return null;
            // }

            String fileName = property.getId() + "_" + hash + ".jpg";
            String storagePath = imageStorageService.store(imageBytes, fileName);

            PropertyImage image = new PropertyImage();
            image.setProperty(property);
            image.setOriginalUrl(imageUrl);
            image.setStoragePath(storagePath);
            image.setImageHash(hash);
            image.setPrimaryImage(primary);
            image.setDisplayOrder(order);
            synchronized (property) {
                property.getImages().add(image);
            }
            return propertyImageRepository.save(image);
        } catch (ImageDownloadException e) {
            log.warn("Skipping broken image URL {} for property {}", imageUrl, property.getId());
            return null;
        }
    }

    @Override
    public void processPrimaryImage(Property property, String primaryPhotoUrl) {
        processAndSave(property, primaryPhotoUrl, true, 0);
    }

    @Override
    public void processAltPhotos(Property property, String altPhotos) {
        if (altPhotos == null || altPhotos.isBlank()) return;
        String[] urls = altPhotos.split(",");
        int order = 1, count = 0;
        for (String url : urls) {
            if (count++ > 8) return;
            processAndSave(property, url.trim(), false, order++);
        }
    }

    @Override
    public List<PropertyImageDTO> getImages(Long propertyId) {
        return propertyImageRepository
                .findByPropertyId(propertyId).stream()
                .sorted(Comparator.comparingInt(PropertyImage::getDisplayOrder))
                .map(image -> {

                    PropertyImageDTO dto = propertyImageMapper.toDTO(image);

                    String key = image.getStoragePath().substring(
                                     image.getStoragePath().lastIndexOf("/") + 1);

                    dto.setStoragePath(s3Service.generatePresignedUrl(key));

                    return dto;
                })
                .toList();
    }
}
