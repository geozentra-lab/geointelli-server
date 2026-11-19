package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.dto.ForecastResponse;
import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.dto.ValuationResponse.Reason;
import com.geointelli.ai.property.service.service.ForecastService;
import com.geointelli.ai.property.service.service.ValuationService;

import lombok.RequiredArgsConstructor;

/**
 * Reuses valuation display rules without retrieving comparable sales or running
 * the model.
 */
@Service
@RequiredArgsConstructor
public class ForecastServiceImpl implements ForecastService {
    private final ValuationService valuationService;

    /** {@inheritDoc} */
    @Override
    public ForecastResponse getForecast(Long propertyId) {
        ValuationResponse valuation = valuationService.getValuationWithoutComparables(propertyId);
        boolean available = valuation.forecasts() != null;
        List<Reason> reasons = new ArrayList<>(valuation.reasons());
        if (!available && !"unsupported".equals(valuation.status())) {
            reasons.add(new Reason("forecast_unavailable", "No forecast is available from the stored model output."));
        }
        return new ForecastResponse(valuation.status(), available, List.copyOf(reasons), "USD",
                valuation.estimate() == null ? null : valuation.estimate().confidence(), valuation.forecasts(),
                valuation.method(), valuation.reproducibility(), valuation.notes());
    }
}
