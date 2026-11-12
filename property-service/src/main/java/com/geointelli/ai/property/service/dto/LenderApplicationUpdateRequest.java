package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;
import com.geointelli.ai.property.service.entity.LenderLoanType;
import lombok.Getter;

/** Omitted fields stay unchanged; optional email, property and notes may be explicitly cleared. */
@Getter
public class LenderApplicationUpdateRequest {
    private String applicantName;
    @JsonIgnore private boolean applicantNameProvided;
    @JsonSetter public void setApplicantName(String value) { applicantName = value; applicantNameProvided = true; }
    private String applicantEmail;
    @JsonIgnore private boolean applicantEmailProvided;
    @JsonSetter public void setApplicantEmail(String value) { applicantEmail = value; applicantEmailProvided = true; }
    private Long propertyId;
    @JsonIgnore private boolean propertyIdProvided;
    @JsonSetter public void setPropertyId(Long value) { propertyId = value; propertyIdProvided = true; }
    private BigDecimal requestedAmount;
    @JsonIgnore private boolean requestedAmountProvided;
    @JsonSetter public void setRequestedAmount(BigDecimal value) { requestedAmount = value; requestedAmountProvided = true; }
    private String currency;
    @JsonIgnore private boolean currencyProvided;
    @JsonSetter public void setCurrency(String value) { currency = value; currencyProvided = true; }
    private LenderApplicationStage stage;
    @JsonIgnore private boolean stageProvided;
    @JsonSetter public void setStage(LenderApplicationStage value) { stage = value; stageProvided = true; }
    private BigDecimal approvedAmount;
    @JsonIgnore private boolean approvedAmountProvided;
    @JsonSetter public void setApprovedAmount(BigDecimal value) { approvedAmount = value; approvedAmountProvided = true; }
    private BigDecimal fundedAmount;
    @JsonIgnore private boolean fundedAmountProvided;
    @JsonSetter public void setFundedAmount(BigDecimal value) { fundedAmount = value; fundedAmountProvided = true; }
    private LenderLoanType loanType;
    @JsonIgnore private boolean loanTypeProvided;
    @JsonSetter public void setLoanType(LenderLoanType value) { loanType = value; loanTypeProvided = true; }
    private String notes;
    @JsonIgnore private boolean notesProvided;
    @JsonSetter public void setNotes(String value) { notes = value; notesProvided = true; }
}

