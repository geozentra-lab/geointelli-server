package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class SaleDTO {

    private Long id;
    
    private LocalDate saleDate;

    private BigDecimal salePrice;

    private String saleType;

    private String instrumentNumber;

}