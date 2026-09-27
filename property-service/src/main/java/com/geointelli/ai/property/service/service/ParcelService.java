package com.geointelli.ai.property.service.service;

import java.util.List;
import java.util.Map;

import com.geointelli.ai.property.service.dto.ParcelDTO;
import com.geointelli.ai.property.service.entity.Parcel;

public interface ParcelService {

    public List<ParcelDTO> getParcelsWithinBoundingBox(double xmin,double ymin,double xmax,double ymax);
    public Map<Long, Parcel> preloadParcels();
    public List<String> getAllFolios(Long countyId);
    public List<ParcelDTO> getByFolio(String folio, Long countyId);
}
