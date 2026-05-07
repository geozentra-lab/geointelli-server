package com.geointelli.ai.property.service.dto;

import org.locationtech.jts.geom.MultiPolygon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
public class ParcelDTO {

    private Long id;
    
    private String folio;

    private GeometryDTO geom;
}
