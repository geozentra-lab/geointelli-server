package com.geointelli.ai.property.service.client.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SalesInfo {

    @JsonProperty("DateOfSale")
    private LocalDate saleDate;

    @JsonProperty("DocumentStamps")
    private double documentStamps;

    @JsonProperty("EncodedRecordBookAndPage")
    private String encodedRecordBookAndPage;

    @JsonProperty("GranteeName1")
    private String granteeName1;

    @JsonProperty("GranteeName2")
    private String granteeName2;

    @JsonProperty("GrantorName1")
    private String grantorName1;

    @JsonProperty("GrantorName2")
    private String grantorName2;

    @JsonProperty("Message")
    private String message;

    @JsonProperty("OfficialRecordBook")
    private String officialRecordBook;

    @JsonProperty("OfficialRecordPage")
    private String officialRecordPage;

    @JsonProperty("QualificationDescription")
    private String qualificationDescription;

    @JsonProperty("QualifiedFlag")
    private String qualifiedFlag;

    @JsonProperty("QualifiedSYear")
    private String qualifiedSYear;

    @JsonProperty("QualifiedSourceCode")
    private String qualifiedSourceCode;

    @JsonProperty("ReasonCode")
    private String reasonCode;

    @JsonProperty("ReviewCode")
    private String reviewCode;

    @JsonProperty("SaleId")
    private String saleId;

    @JsonProperty("SaleInstrument")
    private String saleInstrument;

    @JsonProperty("SalePrice")
    private double salePrice;

    @JsonProperty("VacantFlag")
    private Boolean vacantFlag;

    @JsonProperty("ValidCode")
    private String validCode;

    @JsonProperty("VerifyCode")
    private String verifyCode;

    public void setSaleDate(String dateOfSale) {
        if (dateOfSale == null || dateOfSale.isEmpty()) {
            this.saleDate = null;
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");
        this.saleDate = LocalDate.parse(dateOfSale, formatter);
    }
}
