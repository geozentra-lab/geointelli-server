package com.geointelli.ai.property.service.dto;
import lombok.Data;

@Data
public class ParcelDTO {

    private Long id;

    private Long propertyId;
    
    private String folio;

    private GeometryDTO geom;
}
