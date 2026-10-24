package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.geointelli.ai.property.service.entity.HoldingOccupancyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Current occupancy and contractual rent, independent of collected income and expense records. */
@Getter
@Setter
@Schema(description = "Replaced as a whole on PATCH. Monthly contractual rent in USD for the full property; "
    + "not actual collected income. Lease dates and nonzero rent require RENTED status.")
public class HoldingOccupancyDTO {
    @NotNull
    private HoldingOccupancyStatus status = HoldingOccupancyStatus.UNKNOWN;
    @DecimalMin("0") @Digits(integer = 12, fraction = 2)
    private BigDecimal monthlyRent;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate leaseStartDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate leaseEndDate;
}
