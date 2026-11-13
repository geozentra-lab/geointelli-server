package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.ValuationHistoryEntry;
import com.geointelli.ai.property.service.dto.ValuationModelEvidence;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.ValuationHistoryReader;
import com.geointelli.ai.property.service.service.ValuationHistoryService;
import com.geointelli.ai.property.service.service.ValuationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Displays immutable Python publications without consulting current property facts or sales. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValuationHistoryServiceImpl implements ValuationHistoryService {
    private final PropertyRepository properties;
    private final ValuationHistoryReader reader;
    private final ValuationService valuationService;
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    /** {@inheritDoc} */
    @Override
    public PageResponse<ValuationHistoryEntry> getHistory(Long propertyId, int page, int size) {
        if (propertyId == null || propertyId <= 0) throw new IllegalArgumentException("propertyId must be positive");
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        if (!properties.existsById(propertyId)) throw new PropertyNotFoundException("Property not found: " + propertyId);
        var history = reader.findByPropertyId(propertyId, PageRequest.of(page, size));
        var entries = history.map(snapshot -> {
            var prediction = snapshot.prediction();
            if (!propertyId.equals(prediction.getPropertyId()))
                throw new IllegalStateException("History store returned a snapshot for another property");
            var valuation = valuationService.explainSnapshot(prediction, snapshot.supportingComparables());
            return new ValuationHistoryEntry(prediction.getRunId(), prediction.getInputHash(), snapshot.archivedAt(),
                valuation, snapshot.id(), snapshot.provenance(),
                "SEEDED_CURRENT".equals(snapshot.provenance()) ? "recorded before history" : "published valuation",
                snapshot.scoredAt(), snapshot.loadedAt(), snapshot.rowSha256(),
                "unsupported".equals(valuation.status()) ? null : evidence(snapshot.evidence()), snapshot.run());
        });
        return PageResponse.from(entries);
    }

    private ValuationModelEvidence evidence(String raw) {
        if (raw == null || raw.isBlank()) return new ValuationModelEvidence("NOT_RECORDED",
            "Model inputs were not retained for this version", null, null);
        try {
            JsonNode root = json.readTree(raw);
            if (!root.isObject()) throw new IllegalArgumentException("Evidence must be an object");
            var sale = root.path("last_sale");
            var nearby = root.path("nearby_sales");
            return new ValuationModelEvidence("RECORDED", "Frozen inputs used by this model run",
                sale.isObject() ? new ValuationModelEvidence.LastSale(decimal(sale, "price"), date(sale, "date")) : null,
                nearby.isObject() ? new ValuationModelEvidence.NearbySales(decimal(nearby, "median_price"),
                    integer(nearby, "count"), decimal(nearby, "price_per_sqft"), decimal(nearby, "radius_deg"),
                    integer(nearby, "lookback_days"), date(nearby, "window_end")) : null);
        } catch (Exception error) {
            log.warn("Invalid archived valuation evidence; current evidence will not be substituted");
            return new ValuationModelEvidence("INVALID", "Archived model evidence is unavailable", null, null);
        }
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).decimalValue() : null;
    }

    private Integer integer(JsonNode node, String field) {
        return node.path(field).isIntegralNumber() ? node.path(field).intValue() : null;
    }

    private LocalDate date(JsonNode node, String field) {
        return node.path(field).isTextual() ? LocalDate.parse(node.path(field).textValue()) : null;
    }
}
