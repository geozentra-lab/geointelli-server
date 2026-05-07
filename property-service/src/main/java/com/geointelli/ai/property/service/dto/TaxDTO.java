package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class TaxDTO {

    private Long id;
    
    private BigDecimal cityExemptionValue;

    private BigDecimal cityTaxableValue;

    private BigDecimal countyExemptionValue;

    private BigDecimal countyTaxableValue;

    private BigDecimal regionalExemptionValue;

    private BigDecimal regionalTaxableValue;

    private BigDecimal schoolExemptionValue;

    private BigDecimal schoolTaxableValue;

    private Integer year;

    private String message;

}