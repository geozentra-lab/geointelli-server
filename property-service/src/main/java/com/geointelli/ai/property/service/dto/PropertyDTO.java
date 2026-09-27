package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class PropertyDTO {

    private Long id;

    private String folio;

    private Long countyId;
    private String countyName;
    private Long stateId;
    private String stateCode;
    private String stateName;

    private String parentFolio;

    private Long parentPropertyId;

    private BigDecimal bathroomCount;

    private BigDecimal bedroomCount;

    private Double halfBathroomCount;

    private BigDecimal buildingActualArea;

    private BigDecimal buildingBaseArea;

    private BigDecimal buildingEffectiveArea;

    private BigDecimal buildingGrossArea;

    private BigDecimal buildingHeatedArea;

    private String dorCode;

    private String dorDescription;

    private String neighborhood;

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
