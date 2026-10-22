package com.geointelli.ai.property.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PropertyExtraFeaturesDTO {

    private Long propertyId;
    private Integer rollYear;
    private boolean history;
    private List<ExtraFeatureDTO> features;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
}
