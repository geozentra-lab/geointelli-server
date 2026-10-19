package com.geointelli.ai.property.service.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserPreferencesDTO {
    @DecimalMin("0")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal minBudget;

    @DecimalMin("0")
    @Digits(integer = 13, fraction = 2)
    private BigDecimal maxBudget;

    @NotNull
    @Size(max = 20)
    private List<@NotNull @Valid CountyLocationDTO> targetLocations = new ArrayList<>();

    @NotNull
    @Size(max = 20)
    private List<@NotBlank @Size(max = 200) String> investmentGoals = new ArrayList<>();
}
