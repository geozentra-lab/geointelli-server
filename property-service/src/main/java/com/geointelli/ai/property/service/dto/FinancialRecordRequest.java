package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.geointelli.ai.property.service.entity.FinancialRecordCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** Partial corrections preserve omitted fields; explicit null clears optional description or period dates. */
@Getter
@Schema(description = "Actual cash received or paid, not an estimate. POST requires category, amount and transactionDate. "
    + "PATCH changes only supplied fields. Amounts are positive USD totals for the full property before ownership allocation. "
    + "Income/expense direction comes from category. Period dates must both exist or both be null after the update.")
public class FinancialRecordRequest {
    @NotNull(groups = PortfolioCreate.class)
    @Schema(example = "RENT")
    private FinancialRecordCategory category;

    @NotNull(groups = PortfolioCreate.class)
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 12, fraction = 2)
    @Schema(example = "2500.00")
    private BigDecimal amount;

    @Pattern(regexp = "USD", message = "must be USD")
    @Schema(description = "USD only, consistent with holdings; defaults to USD on POST.", example = "USD")
    private String currency = "USD";

    @NotNull(groups = PortfolioCreate.class)
    @PastOrPresent
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "Date cash was actually received or paid; cannot be in the future.", example = "2026-10-01")
    private LocalDate transactionDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "First covered day, inclusive. Coverage may extend beyond the transaction date.", example = "2026-10-01")
    private LocalDate periodStart;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "Last covered day, inclusive. Must be on or after periodStart.", example = "2026-10-31")
    private LocalDate periodEnd;

    @Size(max = 2000)
    @Schema(example = "October rent received")
    private String description;

    @JsonIgnore
    private final Set<String> providedFields = new HashSet<>();

    public boolean hasField(String field) { return providedFields.contains(field); }

    @JsonSetter
    public void setCategory(FinancialRecordCategory value) { category = value; providedFields.add("category"); }
    @JsonSetter
    public void setAmount(BigDecimal value) { amount = value; providedFields.add("amount"); }
    @JsonSetter
    public void setCurrency(String value) { currency = value; providedFields.add("currency"); }
    @JsonSetter
    public void setTransactionDate(LocalDate value) { transactionDate = value; providedFields.add("transactionDate"); }
    @JsonSetter
    public void setPeriodStart(LocalDate value) { periodStart = value; providedFields.add("periodStart"); }
    @JsonSetter
    public void setPeriodEnd(LocalDate value) { periodEnd = value; providedFields.add("periodEnd"); }
    @JsonSetter
    public void setDescription(String value) { description = value; providedFields.add("description"); }

    @AssertTrue(message = "category, amount, currency and transactionDate cannot be cleared")
    @JsonIgnore
    public boolean isRequiredFieldsValid() {
        return (!hasField("category") || category != null)
            && (!hasField("amount") || amount != null)
            && (!hasField("currency") || currency != null)
            && (!hasField("transactionDate") || transactionDate != null);
    }
}
