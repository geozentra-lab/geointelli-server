package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class SaleDTO {
    private Long id;

    // Sale information
    private Integer saleId;
    private LocalDate saleDate;
    private BigDecimal salePrice;

    // Recording information
    private String officialRecordBook;
    private String officialRecordPage;
    private String encodedRecordBookAndPage;

    // Instrument
    private String saleInstrument;
    private Integer documentStamps;

    // Qualification
    private String qualifiedFlag;
    private String qualificationDescription;
    private Integer qualifiedSYear;
    private String qualifiedSourceCode;
    private String reasonCode;
    private String reviewCode;

    // Parties
    private String grantorName1;
    private String grantorName2;
    private String granteeName1;
    private String granteeName2;

    private Boolean vacantFlag;

    private String message;

}