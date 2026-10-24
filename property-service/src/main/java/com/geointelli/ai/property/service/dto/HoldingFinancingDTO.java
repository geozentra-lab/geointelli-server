package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.geointelli.ai.property.service.entity.HoldingFinancingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Getter;
import lombok.Setter;

/** Reported loan facts; amounts belong to the full property and are denominated in USD. */
@Getter
@Setter
@Schema(description = "Replaced as a whole on PATCH. USD amounts for the full property, not ownership-adjusted. "
    + "monthlyPayment is the reported monthly loan payment; it is not a calculated ownership cost. "
    + "Outstanding balance requires balanceAsOfDate. Null amounts mean unknown.")
public class HoldingFinancingDTO {
    @NotNull
    private HoldingFinancingType type = HoldingFinancingType.UNKNOWN;
    @DecimalMin("0") @Digits(integer = 12, fraction = 2)
    @Schema(description = "Original loan principal in USD; not the current outstanding balance.", example = "280000.00")
    private BigDecimal loanAmount;
    @DecimalMin("0") @Digits(integer = 12, fraction = 2)
    @Schema(description = "Reported principal still owed as of balanceAsOfDate, in USD.")
    private BigDecimal outstandingBalance;
    @PastOrPresent
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate balanceAsOfDate;
    @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
    private BigDecimal interestRatePercent;
    @Min(1) @Max(600)
    private Integer loanTermMonths;
    @DecimalMin("0") @Digits(integer = 12, fraction = 2)
    private BigDecimal monthlyPayment;
}
