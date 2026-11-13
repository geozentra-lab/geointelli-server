package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.ComparableCandidateRequest;
import com.geointelli.ai.property.service.dto.ComparableResponse;
import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.dto.ValuationResponse.Evidence;
import com.geointelli.ai.property.service.dto.ValuationResponse.Estimate;
import com.geointelli.ai.property.service.dto.ValuationResponse.Explanation;
import com.geointelli.ai.property.service.dto.ValuationResponse.Factor;
import com.geointelli.ai.property.service.dto.ValuationResponse.Forecasts;
import com.geointelli.ai.property.service.dto.ValuationResponse.LastSale;
import com.geointelli.ai.property.service.dto.ValuationResponse.Method;
import com.geointelli.ai.property.service.dto.ValuationResponse.Projection;
import com.geointelli.ai.property.service.dto.ValuationResponse.Range;
import com.geointelli.ai.property.service.dto.ValuationResponse.Reason;
import com.geointelli.ai.property.service.dto.ValuationResponse.Reproducibility;
import com.geointelli.ai.property.service.dto.ValuationResponse.SupportingComparables;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.PropertyValuePredictionRepository;
import com.geointelli.ai.property.service.service.ComparableService;
import com.geointelli.ai.property.service.service.ValuationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Display-only mapping of stored predictions; comparables run in their own existing read transaction. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValuationServiceImpl implements ValuationService {
    private static final String EXPLANATION_NOTE = "These factors explain the model's estimate. "
        + "Comparable sales are shown separately as supporting evidence.";
    private final PropertyRepository propertyRepository;
    private final PropertyValuePredictionRepository predictions;
    private final ComparableService comparableService;
    private final ObjectMapper json;

    /** {@inheritDoc} */
    @Override
    public ValuationResponse getValuation(Long propertyId) {
        if (propertyId == null || propertyId <= 0) throw new IllegalArgumentException("propertyId must be positive");
        if (!propertyRepository.existsById(propertyId))
            throw new PropertyNotFoundException("Property not found: " + propertyId);
        PropertyValuePrediction prediction = predictions.findByPropertyId(propertyId).orElse(null);
        if (prediction == null) return unsupported(List.of(reason("not_modelled")), null);

        return display(prediction, false, null);
    }

    /** {@inheritDoc} */
    @Override
    public ValuationResponse explainSnapshot(PropertyValuePrediction snapshot, ComparableResponse supportingComparables) {
        if (snapshot == null || snapshot.getPropertyId() == null || snapshot.getPropertyId() <= 0)
            throw new IllegalArgumentException("A snapshot with a positive propertyId is required");
        return display(snapshot, true, supportingComparables);
    }

    private ValuationResponse display(PropertyValuePrediction prediction, boolean historical, ComparableResponse archivedComparables) {
        Long propertyId = prediction.getPropertyId();
        Method method = new Method(prediction.getValuationMethod(), prediction.getModelVersion(),
            prediction.getPredictionDate(), prediction.getDataThrough(), prediction.getCreatedAt());
        List<Reason> reasons = new ArrayList<>();
        for (String code : strings(prediction.getStatusReasons(), "status_reasons", propertyId)) reasons.add(reason(code));
        String status = prediction.getValuationStatus();
        if (!List.of("supported", "limited", "unsupported").contains(status == null ? "" : status)) {
            log.warn("Unknown valuation status for property {}: {}", propertyId, status);
            if (status != null && !status.isBlank()) reasons.add(new Reason(status, status));
            reasons.add(reason("no_estimate"));
            return unsupported(reasons, method);
        }
        if ("unsupported".equals(status) || prediction.getPredictedPrice() == null) {
            if (reasons.isEmpty() || prediction.getPredictedPrice() == null) reasons.add(reason("no_estimate"));
            return unsupported(reasons, method);
        }

        List<String> notes = new ArrayList<>(strings(prediction.getNotes(), "notes", propertyId));
        Estimate estimate = new Estimate(prediction.getPredictedPrice(),
            new Range(prediction.getPredictedPriceLow(), prediction.getPredictedPriceHigh()),
            new Range(prediction.getPredictedPriceLow90(), prediction.getPredictedPriceHigh90()), prediction.getConfidence());
        Forecasts forecasts = forecasts(prediction);
        if (forecasts != null) notes.add("Three- and seven-year forecasts are trend scenarios; forecast ranges are illustrative.");
        Explanation explanation = explanation(prediction, notes, historical);
        SupportingComparables comparables = historical ? archivedComparables(archivedComparables) : supportingComparables(propertyId);
        if (historical && archivedComparables == null)
            notes.add("Supporting comparables were not retained for this valuation; current sales are not substituted.");
        Evidence evidence = new Evidence(new LastSale(prediction.getHasSaleHistory(), prediction.getLastSalePrice(),
            prediction.getLastSaleDate()), comparables);
        return new ValuationResponse(status, List.copyOf(reasons), estimate, forecasts, explanation, evidence, method,
            new Reproducibility(prediction.getRunId(), prediction.getInputHash()), List.copyOf(notes));
    }

    private ValuationResponse unsupported(List<Reason> reasons, Method method) {
        return new ValuationResponse("unsupported", List.copyOf(reasons), null, null, null, null, method, null, null);
    }

    private Reason reason(String code) {
        return new Reason(code, ValuationDisplayText.reason(code));
    }

    private Forecasts forecasts(PropertyValuePrediction p) {
        if (p.getForecast1y() == null) return null;
        return new Forecasts(new Projection(p.getForecast1y(), p.getForecast1yLow(), p.getForecast1yHigh()),
            new Projection(p.getForecast3y(), p.getForecast3yLow(), p.getForecast3yHigh()),
            new Projection(p.getForecast7y(), p.getForecast7yLow(), p.getForecast7yHigh()));
    }

    private Explanation explanation(PropertyValuePrediction p, List<String> notes, boolean historical) {
        JsonNode value = parse(p.getValuationFactors(), "valuation_factors", p.getPropertyId());
        BigDecimal baseline = value == null ? null : number(value.get("baseline"));
        List<Factor> factors = new ArrayList<>();
        JsonNode entries = value == null ? null : value.get("factors");
        if (entries != null && entries.isArray()) {
            for (JsonNode entry : entries) {
                JsonNode codeNode = entry.get("f");
                if (codeNode == null || !codeNode.isTextual()) {
                    log.warn("Invalid valuation factor for property {}", p.getPropertyId());
                    continue;
                }
                String code = codeNode.textValue();
                String label = ValuationDisplayText.label(code);
                BigDecimal pct = number(entry.get("pct"));
                factors.add(new Factor(code, label, pct, number(entry.get("usd")),
                    ValuationDisplayText.factorText(code, label, pct)));
            }
        } else {
            notes.add("Model explanation factors are unavailable.");
        }
        String timing = historical ? p.getPredictionDate() == null ? " at the recorded valuation time"
            : " as of " + p.getPredictionDate() : " today";
        String summary = baseline == null ? "This property is estimated at " + money(p.getPredictedPrice()) + "."
            : "A typical Miami-Dade home is valued at about " + money(baseline)
                + timing + ". This property is estimated at " + money(p.getPredictedPrice()) + ".";
        return new Explanation(baseline, summary, List.copyOf(factors), EXPLANATION_NOTE);
    }

    private SupportingComparables archivedComparables(ComparableResponse archived) {
        return new SupportingComparables("Supporting comparables archived with this version, not the model's calculation.",
            archived == null ? "NOT_RECORDED" : archived.getStatus(), archived);
    }

    private SupportingComparables supportingComparables(Long propertyId) {
        String label = "Comparable sales selected separately as supporting evidence, not the model's calculation.";
        try {
            ComparableCandidateRequest request = new ComparableCandidateRequest();
            request.setLimit(5);
            var response = comparableService.findComparables(propertyId, request);
            return new SupportingComparables(label, response.getStatus(), response);
        } catch (DataAccessException | TransactionException failure) {
            log.warn("Supporting comparables unavailable for property {} ({})", propertyId, failure.getClass().getSimpleName());
            return new SupportingComparables(label, "UNAVAILABLE", null);
        }
    }

    private JsonNode parse(String text, String field, Long propertyId) {
        if (text == null || text.isBlank()) return null;
        try {
            return json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(text);
        } catch (JsonProcessingException failure) {
            log.warn("Invalid stored valuation JSON in {} for property {}", field, propertyId);
            return null;
        }
    }

    private List<String> strings(String text, String field, Long propertyId) {
        JsonNode value = parse(text, field, propertyId);
        List<String> result = new ArrayList<>();
        if (value != null && value.isArray()) {
            for (JsonNode entry : value) {
                if (entry.isTextual()) result.add(entry.textValue());
                else log.warn("Non-string valuation {} entry for property {}", field, propertyId);
            }
        } else if (value != null) log.warn("Expected valuation array in {} for property {}", field, propertyId);
        return result;
    }

    private BigDecimal number(JsonNode value) {
        return value != null && value.isNumber() ? value.decimalValue() : null;
    }

    private String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        format.setMaximumFractionDigits(0);
        format.setMinimumFractionDigits(0);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }
}
