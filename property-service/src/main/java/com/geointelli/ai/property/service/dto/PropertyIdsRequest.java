package com.geointelli.ai.property.service.dto;

import java.util.List;

import lombok.Data;

@Data
public class PropertyIdsRequest {
    private List<Long> propertyIds;
}
