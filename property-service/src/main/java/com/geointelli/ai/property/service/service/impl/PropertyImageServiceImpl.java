package com.geointelli.ai.property.service.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;

import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.dto.PropertyImageDTO;
import com.geointelli.ai.property.service.dto.ImageSaveRequest;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImage;
import com.geointelli.ai.property.service.exception.ImageDownloadException;
import com.geointelli.ai.property.service.mapper.PropertyImageMapper;
import com.geointelli.ai.property.service.repository.PropertyImageRepository;
import com.geointelli.ai.property.service.service.ImageDownloadService;
import com.geointelli.ai.property.service.service.ImageStorageService;
import com.geointelli.ai.property.service.service.PropertyImageService;
import com.geointelli.ai.property.service.service.S3Service;
import org.springframework.context.annotation.Lazy;
import com.geointelli.ai.property.service.util.HashUtil;

import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class PropertyImageServiceImpl implements PropertyImageService {
    private final ImageDownloadService imageDownloadService;
    private final PropertyImageRepository propertyImageRepository;
    private final ImageStorageService imageStorageService;
    private final PropertyImageMapper propertyImageMapper;
    private final S3Service s3Service;

    public PropertyImageServiceImpl(ImageDownloadService imageDownloadService,
            PropertyImageRepository propertyImageRepository,
            @Qualifier("s3Storage") ImageStorageService imageStorageService,
            PropertyImageMapper propertyImageMapper, @Lazy S3Service s3Service) {

        this.imageDownloadService = imageDownloadService;
        this.propertyImageRepository = propertyImageRepository;
        this.imageStorageService = imageStorageService;
        this.propertyImageMapper = propertyImageMapper;
        this.s3Service = s3Service;
    }

    @Transactional
    @Override
    public PropertyImage processAndSave(Property property, String imageUrl, boolean primary, int order) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        try {
            boolean urlAlreadyProcessed = property.getImages().stream()
                    .anyMatch(img -> imageUrl.equals(img.getOriginalUrl()));
            if (urlAlreadyProcessed) {
                log.info("Image URL already processed for property {}", property.getId());
                return null;
            }

            ImageSaveRequest request = new ImageSaveRequest();
            request.setContent(imageDownloadService.download(imageUrl));
            request.setExtension(imageExtension(request.getContent()));
            request.setOriginalUrl(imageUrl);
            return save(property, request, primary, order);
        } catch (ImageDownloadException e) {
            log.warn("Skipping broken image for property {}", property.getId());
            return null;
        }
    }

    @Override
    public PropertyImage save(Property property, ImageSaveRequest request, boolean primary, int order) {
        if (request.getContent() == null || request.getContent().length == 0) {
            throw new IllegalArgumentException("Image content is required");
        }
        String extension = request.getExtension();
        if (extension == null || !Set.of("jpg", "jpeg", "png", "webp", "gif").contains(extension)) {
            throw new IllegalArgumentException("Unsupported image extension");
        }
        String hash = HashUtil.sha256(request.getContent());
        if (propertyImageRepository.findByImageHash(hash).isPresent()) return null;
        String fileName = property.getId() + "_" + hash + "." + extension;
        String storagePath = imageStorageService.store(request.getContent(), fileName);
        PropertyImage image = new PropertyImage();
        image.setProperty(property); image.setOriginalUrl(request.getOriginalUrl());
        image.setStoragePath(storagePath); image.setImageHash(hash);
        image.setPrimaryImage(primary); image.setDisplayOrder(order);
        image.setSource(request.getSource()); image.setSourceImageId(request.getSourceImageId());
        image.setContributor(request.getContributor()); image.setLicense(request.getLicense());
        image.setCapturedAt(request.getCapturedAt()); image.setMatchStatus(request.getMatchStatus());
        image.setProcessingNote(request.getProcessingNote());
        image = propertyImageRepository.saveAndFlush(image);
        synchronized (property) { property.getImages().add(image); }
        return image;
    }

    /** Detects the downloaded format so the filename matches the stored bytes. */
    private String imageExtension(byte[] content) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ImageDownloadException("Unsupported image format");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                return "jpeg".equals(format) ? "jpg" : format;
            } finally { reader.dispose(); }
        } catch (IOException error) {
            throw new ImageDownloadException("Cannot read image format", error);
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
    @Transactional(readOnly = true)
    public List<PropertyImageDTO> getImages(Long propertyId) {
        return propertyImageRepository
                .findByPropertyId(propertyId).stream()
                .sorted(Comparator.comparingInt(PropertyImage::getDisplayOrder))
                .map(image -> {

                    PropertyImageDTO dto = propertyImageMapper.toDTO(image);

                    String storagePath = image.getStoragePath();
                    String key = storagePath.startsWith("https://") || storagePath.startsWith("http://")
                        ? URI.create(storagePath).getPath().replaceFirst("^/", "") : storagePath;
                    dto.setStoragePath(s3Service.generatePresignedUrl(key));

                    return dto;
                })
                .toList();
    }
}
