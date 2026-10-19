package com.geointelli.ai.property.service.service;

import java.util.List;
import java.util.Set;

import com.geointelli.ai.property.service.dto.PropertyImageDTO;
import com.geointelli.ai.property.service.dto.ImageSaveRequest;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImage;

public interface PropertyImageService {
    void processPrimaryImage(Property property, String primaryPhotoUrl);
    void processAltPhotos(Property property, String altPhotos);
    PropertyImage processAndSave(Property property,String imageUrl, boolean primary, int order);

    /**
     * Stores processed image bytes and source metadata through the configured storage.
     * @param property owning property
     * @param request image bytes, file extension and optional source information
     * @param primary whether this is the primary image
     * @param order position in the property's image list
     * @return saved image, or null if the image hash already exists
     */
    PropertyImage save(Property property, ImageSaveRequest request, boolean primary, int order);

    public List<PropertyImageDTO> getImages(Long propertyId);                                 
}
