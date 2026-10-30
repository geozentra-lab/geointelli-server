package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
/** Contract for a supported latest full-property valuation on or before the requested date. */
public record PortfolioValuationDTO(BigDecimal amount, String currency, LocalDate valueDate, String source) {}
