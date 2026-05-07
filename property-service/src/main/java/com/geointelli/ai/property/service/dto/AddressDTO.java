package com.geointelli.ai.property.service.dto;

import lombok.Data;

@Data
public class AddressDTO {
    private Long id;

    private String address;

    private int buildingNumber;

    private String city;

    private String houseNumberSuffix;

    private String message;

    private String streetName;

    private int streetNumber;

    private String streetPrefix;

    private String streetSuffix;

    private String streetSuffixDirection;

    private String unit;

    private String zip;

}
