package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.ImageBackfillResultDTO;
import com.geointelli.ai.property.service.dto.MapillarySelectionDTO;

/** Stores a selected image while checking the property again for existing photos. */
public interface MapillaryImagePersistenceService {
    /**
     * Saves one selected image and its attribution through the shared image service.
     * @param propertyId target property
     * @param jobId job that must still own the property claim
     * @param selected screened image and source information
     * @return saved image identifier or a reason for skipping it
     */
    ImageBackfillResultDTO save(Long jobId, long propertyId, MapillarySelectionDTO selected);
}
