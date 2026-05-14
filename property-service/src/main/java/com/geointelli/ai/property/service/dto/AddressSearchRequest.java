package com.geointelli.ai.property.service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddressSearchRequest {
    private Integer streetNumber;
    private String  streetName;
    private String  city;
    private String  zip;
    private String  unit;
    private String  state;
    private String  rawAddress;
}
