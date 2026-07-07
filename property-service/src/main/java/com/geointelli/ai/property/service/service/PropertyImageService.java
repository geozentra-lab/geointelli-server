package com.geointelli.ai.property.service.service;

import java.util.List;
import java.util.Set;

import com.geointelli.ai.property.service.dto.PropertyImageDTO;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyImage;

public interface PropertyImageService {
    void processPrimaryImage(Property property, String primaryPhotoUrl);
    void processAltPhotos(Property property, String altPhotos);
    PropertyImage processAndSave(Property property,String imageUrl, boolean primary, int order);

    public List<PropertyImageDTO> getImages(Long propertyId);                                 
}
