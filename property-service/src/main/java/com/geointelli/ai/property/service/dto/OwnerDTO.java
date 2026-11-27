package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class OwnerDTO {

    private Long id;

    private String name;

    private String description;

    private String role;

    private BigDecimal percentageOwn;

    private String shortDescription;

    private String tenancyCd;

    private Boolean marriedFlag;

    private String message;

}