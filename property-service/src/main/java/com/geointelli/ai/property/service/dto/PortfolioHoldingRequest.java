package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

/** Preserves omitted-versus-null fields so partial updates cannot silently erase acquisition facts. */
@Getter
@Schema(description = "propertyId is required on POST and immutable. PATCH leaves omitted fields unchanged. "
    + "Explicit null clears optional fields; financing/occupancy:null resets that section to UNKNOWN. "
    + "Supplied financing and occupancy objects replace their entire section. "
    + "All monetary amounts are USD for the full property before ownership-share allocation.")
public class PortfolioHoldingRequest {
    @NotNull(groups = PortfolioCreate.class) @Positive
    @Schema(example = "100000", description = "Internal property ID, not a folio or external source ID.")
    private Long propertyId;
    @PastOrPresent
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDate acquisitionDate;
    @DecimalMin("0") @Digits(integer = 12, fraction = 2)
    @Schema(example = "350000.00", description = "Reported acquisition price in USD for the full property.")
    private BigDecimal purchasePrice;
    @DecimalMin("0") @Digits(integer = 12, fraction = 2)
    @Schema(example = "8000.00", description = "Reported one-time acquisition costs in USD for the full property.")
    private BigDecimal acquisitionCosts;
    @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2)
    @Schema(description = "Percentage owned, greater than zero and at most 100; defaults to 100 on POST.")
    private BigDecimal ownershipSharePercent;
    @Valid
    private HoldingFinancingDTO financing;
    @Valid
    private HoldingOccupancyDTO occupancy;

    @JsonIgnore
    private final Set<String> providedFields = new HashSet<>();

    public boolean hasField(String field) {
        return providedFields.contains(field);
    }

    @JsonSetter
    public void setPropertyId(Long value) { propertyId = value; providedFields.add("propertyId"); }
    @JsonSetter
    public void setAcquisitionDate(LocalDate value) { acquisitionDate = value; providedFields.add("acquisitionDate"); }
    @JsonSetter
    public void setPurchasePrice(BigDecimal value) { purchasePrice = value; providedFields.add("purchasePrice"); }
    @JsonSetter
    public void setAcquisitionCosts(BigDecimal value) { acquisitionCosts = value; providedFields.add("acquisitionCosts"); }
    @JsonSetter
    public void setOwnershipSharePercent(BigDecimal value) {
        ownershipSharePercent = value; providedFields.add("ownershipSharePercent");
    }
    @JsonSetter
    public void setFinancing(HoldingFinancingDTO value) { financing = value; providedFields.add("financing"); }
    @JsonSetter
    public void setOccupancy(HoldingOccupancyDTO value) { occupancy = value; providedFields.add("occupancy"); }

    @AssertTrue(message = "ownershipSharePercent must not be null when supplied")
    @JsonIgnore
    public boolean isOwnershipShareValid() {
        return !hasField("ownershipSharePercent") || ownershipSharePercent != null;
    }

    @AssertTrue(message = "propertyId must not be null when supplied")
    @JsonIgnore
    public boolean isPropertyIdValid() {
        return !hasField("propertyId") || propertyId != null;
    }
}
