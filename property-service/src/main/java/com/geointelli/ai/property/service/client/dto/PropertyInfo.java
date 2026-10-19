package com.geointelli.ai.property.service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@ToString
public class PropertyInfo {
    @JsonProperty("BathroomCount")
    private Integer bathroomCount;

    @JsonProperty("BedroomCount")
    private Integer bedroomCount;

    @JsonProperty("HalfBathroomCount")
    private Double halfBathroomCount;

    @JsonProperty("BuildingActualArea")
    private Integer buildingActualArea;

    @JsonProperty("BuildingBaseArea")
    private Integer buildingBaseArea;

    @JsonProperty("BuildingEffectiveArea")
    private Integer buildingEffectiveArea;

    @JsonProperty("BuildingGrossArea")
    private Integer buildingGrossArea;

    @JsonProperty("BuildingHeatedArea")
    private Integer buildingHeatedArea;

    @JsonProperty("DORCode")
    private String dorCode;

    @JsonProperty("DORDescription")
    private String dorDescription;

    @JsonProperty("Neighborhood")
    private Integer neighborhood;
     
    @JsonProperty("NeighborhoodDescription")
    private String neighborhoodDescription;

    @JsonProperty("FolioNumber")
    private String folio;

    @JsonProperty("ParentFolio")
    private String parentFolio;

    @JsonProperty("LotSize")
    private Double lotSize;

    @JsonProperty("FloorCount")
    private Integer floorCount;

    @JsonProperty("UnitCount")
    private Integer unitCount;

    @JsonProperty("YearBuilt")
    private String yearBuilt;

    @JsonProperty("Municipality")
    private String municipality;

    @JsonProperty("Subdivision")
    private String subdivision;

    @JsonProperty("PrimaryZone")
    private String primaryZone;

    @JsonProperty("PrimaryZoneDescription")
    private String primaryZoneDescription;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("ShowCurrentValuesFlag")
    private String showCurrentValuesFlag;

    @JsonProperty("DORDescriptionCurrent")
    private String dorDescriptionCurrent;

    @JsonProperty("EncodedFolioAndTaxYear")
    private String encodedFolioAndTaxYear;

    @JsonProperty("HxBaseYear")
    private Integer hxBaseYear;

    @JsonProperty("PercentHomesteadCapped")
    private Double percentHomesteadCapped;

    @JsonProperty("PlatBook")
    private String platBook;

    @JsonProperty("PlatPage")
    private String platPage;

    @JsonProperty("SubdivisionDescription")
    private String subdivisionDescription;

    @JsonProperty("YearAnnexed")
    private Integer yearAnnexed;

    @JsonProperty("Message")
    private String message;

    public void setFolio(String folio) {
    if (folio != null) {
        this.folio = folio.replace("-", "");
    } else {
        this.folio = null;
    }
}
}
