package com.geointelli.ai.property.service.dto;

import com.geointelli.ai.property.service.entity.LenderLoanType;

import java.math.BigDecimal;

/** Initial manual application; starts in DRAFT and accepts USD amounts with at most two decimal places. */
public record LenderApplicationCreateRequest(String applicantName, String applicantEmail, Long propertyId,
        BigDecimal requestedAmount, String currency, String notes, LenderLoanType loanType) {}

