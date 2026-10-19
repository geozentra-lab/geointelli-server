package com.geointelli.ai.property.service.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.dto.*;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImage;
import com.geointelli.ai.property.service.repository.PropertyImageRepository;
import com.geointelli.ai.property.service.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/** Checks claim ownership and saves an explicitly unverified image association. */
@Service
public class MapillaryImagePersistenceServiceImpl implements MapillaryImagePersistenceService {
    private final EntityManager entityManager;
    private final PropertyImageRepository images;
    private final PropertyImageService imageService;
    private final ImageBackfillClaimService claims;

    public MapillaryImagePersistenceServiceImpl(EntityManager entityManager, PropertyImageRepository images,
            PropertyImageService imageService, ImageBackfillClaimService claims) {
        this.entityManager = entityManager; this.images = images; this.imageService = imageService;
        this.claims = claims;
    }

    @Override
    @Transactional
    public ImageBackfillResultDTO save(Long jobId, long propertyId, MapillarySelectionDTO selected) {
        Property property = entityManager.find(Property.class, propertyId, LockModeType.PESSIMISTIC_WRITE);
        if (property == null) return new ImageBackfillResultDTO(propertyId, "SKIPPED", "PROPERTY_REMOVED", null);
        // Use the same property-then-claim lock order as batch claiming.
        if (!claims.lockOwned(jobId, propertyId)) return new ImageBackfillResultDTO(propertyId, "SKIPPED", "CLAIM_LOST", null);
        if (images.existsByPropertyId(propertyId)) return new ImageBackfillResultDTO(propertyId, "SKIPPED", "IMAGE_ALREADY_EXISTS", null);
        ImageSaveRequest request = new ImageSaveRequest();
        request.setContent(selected.getContent()); request.setExtension(selected.getExtension());
        request.setOriginalUrl(selected.getSourceUrl()); request.setSource("MAPILLARY");
        request.setSourceImageId(selected.getSourceImageId()); request.setContributor(selected.getContributor());
        request.setLicense("CC BY-SA 4.0"); request.setCapturedAt(selected.getCapturedAt());
        request.setMatchStatus("UNVERIFIED"); request.setProcessingNote(selected.getProcessingNote());
        PropertyImage image = imageService.save(property, request, true, 0);
        if (image == null) return new ImageBackfillResultDTO(propertyId, "SKIPPED", "DUPLICATE_IMAGE", null);
        return new ImageBackfillResultDTO(propertyId, "COMPLETED", "IMAGE_SAVED_UNVERIFIED", image.getId());
    }
}
