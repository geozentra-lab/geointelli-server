package com.geointelli.ai.property.service.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class PropertyDTO {

    private Long id;

    private String folio;

    private String parentFolio;

    private Integer bathroomCount;

    private Integer bedroomCount;

    private Double halfBathroomCount;

    private Integer buildingActualArea;

    private Integer buildingBaseArea;

    private Integer buildingEffectiveArea;

    private Integer buildingGrossArea;

    private Integer buildingHeatedArea;

    private String dorCode;

    private String dorDescription;

    private Integer neighborhood;

    private String neighborhoodDescription;

    private Double lotSize;

    private Integer floorCount;

    private Integer unitCount;

    private String yearBuilt;

    private String municipality;

    private String subdivision;

    private String primaryZone;

    private String primaryZoneDescription;

    private String status;

    private String showCurrentValuesFlag;

    private String message;

    private AddressDTO address;

    private List<OwnerDTO> owners;

    private List<AssessmentDTO> assessments;

    private List<BuildingDTO> buildings;

    private List<LandDTO> lands;

    private List<SaleDTO> sales;

    private List<TaxDTO> taxes;

    private List<ParcelDTO> parcels = new ArrayList<>();
}
