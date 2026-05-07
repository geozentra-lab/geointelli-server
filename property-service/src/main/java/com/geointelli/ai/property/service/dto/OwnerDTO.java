package com.geointelli.ai.property.service.dto;

import lombok.Data;

@Data
public class OwnerDTO {

    private Long id;

    private String name;

    private String description;

    private String role;

    private Integer percentageOwn;

    private String shortDescription;

    private String tenancyCd;

    private Boolean marriedFlag;

    private String message;

}