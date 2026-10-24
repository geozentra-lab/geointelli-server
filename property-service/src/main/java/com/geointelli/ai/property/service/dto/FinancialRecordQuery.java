package com.geointelli.ai.property.service.dto;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** Inclusive transaction-date filters; the optional covered period is not used for cash-date filtering. */
@Getter
@Setter
public class FinancialRecordQuery {
    @Min(0)
    private int page = 0;
    @Min(1) @Max(100)
    private int size = 20;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Inclusive lower transaction date, in YYYY-MM-DD format.")
    private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Inclusive upper transaction date, in YYYY-MM-DD format.")
    private LocalDate toDate;
    private FinancialRecordCategory category;

    @AssertTrue(message = "fromDate must not be after toDate")
    @JsonIgnore
    public boolean isDateRangeValid() {
        return fromDate == null || toDate == null || !fromDate.isAfter(toDate);
    }
}
