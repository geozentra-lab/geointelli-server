package com.geointelli.ai.property.service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.geointelli.ai.property.service.dto.ValuationResponse.Forecasts;
import com.geointelli.ai.property.service.dto.ValuationResponse.Method;
import com.geointelli.ai.property.service.dto.ValuationResponse.Reason;
import com.geointelli.ai.property.service.dto.ValuationResponse.Reproducibility;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stored forecasts in USD; projections are distinct from historical sale
 * observations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ForecastResponse(
        @Schema(description = "Underlying valuation support", allowableValues = { "supported", "limited",
                "unsupported" }) String status,
        @Schema(description = "Whether the model supplies a displayable one-year forecast") boolean available,
        List<Reason> reasons, String currency,
        @Schema(description = "Underlying valuation confidence, not a separate forecast confidence score") String confidence,
        Forecasts forecasts, Method method, Reproducibility reproducibility, List<String> notes) {
}
