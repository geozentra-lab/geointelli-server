package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.ComparableResponse;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.PropertyValuePredictionRepository;
import com.geointelli.ai.property.service.service.ComparableService;

/** Historical formatting must never consult today's mutable property, prediction or comparable records. */
class ValuationSnapshotDisplayTest {
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final PropertyValuePredictionRepository predictions = mock(PropertyValuePredictionRepository.class);
    private final ComparableService comparables = mock(ComparableService.class);
    private final ValuationServiceImpl service = new ValuationServiceImpl(properties, predictions, comparables, new ObjectMapper());

    @Test
    void usesOnlyFrozenValuesAndOriginalDatesWithoutInventingArchivedComparables() {
        var snapshot = snapshot();
        var response = service.explainSnapshot(snapshot, null);

        assertThat(response.estimate().value()).isEqualByComparingTo("500000.25");
        assertThat(response.evidence().lastSale().price()).isEqualByComparingTo("250000.00");
        assertThat(response.evidence().comparables().status()).isEqualTo("NOT_RECORDED");
        assertThat(response.evidence().comparables().data()).isNull();
        assertThat(response.method().predictionDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(response.method().createdAt()).isEqualTo(snapshot.getCreatedAt());
        assertThat(response.explanation().summary()).contains("as of 2026-01-01").doesNotContain("today");
        assertThat(response.reproducibility().runId()).isEqualTo("run-january");
        assertThat(response.notes()).anyMatch(note -> note.contains("current sales are not substituted"));
        assertThat(snapshot.getPredictedPrice()).isEqualByComparingTo("500000.25");
        verifyNoInteractions(properties, predictions, comparables);
    }

    @Test
    void usesArchivedSupportingEvidenceWithoutRerankingOrRefreshingIt() {
        ComparableResponse archived = new ComparableResponse();
        archived.setPropertyId(7L);
        archived.setAsOfDate(LocalDate.of(2026, 1, 1));
        archived.setStatus("INSUFFICIENT_DATA");
        archived.setWarnings(List.of("Original archived warning"));

        var response = service.explainSnapshot(snapshot(), archived);

        assertThat(response.evidence().comparables().data()).isSameAs(archived);
        assertThat(response.evidence().comparables().status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(response.evidence().comparables().data().getWarnings()).containsExactly("Original archived warning");
        verifyNoInteractions(properties, predictions, comparables);
    }

    @Test
    void historicalUnsupportedOutputStillSuppressesNumericSections() {
        var snapshot = snapshot();
        snapshot.setValuationStatus("unsupported");
        snapshot.setStatusReasons("[\"above_validated_range\"]");
        var response = service.explainSnapshot(snapshot, null);

        assertThat(response.status()).isEqualTo("unsupported");
        assertThat(response.estimate()).isNull();
        assertThat(response.forecasts()).isNull();
        assertThat(response.explanation()).isNull();
        assertThat(response.evidence()).isNull();
        assertThat(response.method().modelVersion()).isEqualTo("january-model");
        verifyNoInteractions(properties, predictions, comparables);
    }

    private PropertyValuePrediction snapshot() {
        PropertyValuePrediction snapshot = new PropertyValuePrediction();
        snapshot.setPropertyId(7L);
        snapshot.setValuationStatus("supported");
        snapshot.setStatusReasons("[]");
        snapshot.setPredictedPrice(new BigDecimal("500000.25"));
        snapshot.setPredictionDate(LocalDate.of(2026, 1, 1));
        snapshot.setCreatedAt(LocalDateTime.of(2026, 1, 2, 3, 0));
        snapshot.setModelVersion("january-model");
        snapshot.setRunId("run-january");
        snapshot.setHasSaleHistory(true);
        snapshot.setLastSalePrice(new BigDecimal("250000.00"));
        snapshot.setLastSaleDate(LocalDate.of(2010, 5, 1));
        snapshot.setValuationFactors("{\"baseline\":450000,\"factors\":[]}");
        return snapshot;
    }
}
