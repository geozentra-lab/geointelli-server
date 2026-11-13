package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.geointelli.ai.property.service.dto.ValuationHistorySnapshot;
import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.ValuationHistoryReader;
import com.geointelli.ai.property.service.service.ValuationService;

class ValuationHistoryServiceImplTest {
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final ValuationHistoryReader reader = mock(ValuationHistoryReader.class);
    private final ValuationService valuations = mock(ValuationService.class);

    @BeforeEach
    void setup() {
        when(properties.existsById(7L)).thenReturn(true);
    }

    @ParameterizedTest
    @CsvSource({"0,0,20", "7,-1,20", "7,0,0", "7,0,101"})
    void invalidInputIsRejectedBeforeReadingAnyStore(long propertyId, int page, int size) {
        assertThatThrownBy(() -> service().getHistory(propertyId, page, size))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(properties, reader, valuations);
    }

    @Test
    void absentPropertyIs404BeforeTryingHistory() {
        assertThatThrownBy(() -> service().getHistory(8L, 0, 20)).isInstanceOf(PropertyNotFoundException.class);
        verifyNoInteractions(reader, valuations);
    }

    @Test
    void preservesStoredIdentityArchiveTimeAndPagination() {
        var prediction = new PropertyValuePrediction();
        prediction.setPropertyId(7L);
        prediction.setRunId("run-original");
        prediction.setInputHash("original-hash");
        var archivedAt = Instant.parse("2026-01-02T03:00:00Z");
        var snapshot = new ValuationHistorySnapshot(prediction, archivedAt, null);
        var display = new ValuationResponse("supported", List.of(), null, null, null, null, null, null, List.of());
        when(reader.findByPropertyId(7L, PageRequest.of(0, 1)))
            .thenReturn(new PageImpl<>(List.of(snapshot), PageRequest.of(0, 1), 3));
        when(valuations.explainSnapshot(prediction, null)).thenReturn(display);

        var result = service().getHistory(7L, 0, 1);

        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isFalse();
        assertThat(result.content().get(0).archivedAt()).isEqualTo(archivedAt);
        assertThat(result.content().get(0).runId()).isEqualTo("run-original");
        assertThat(result.content().get(0).valuation()).isSameAs(display);
        verify(valuations, never()).getValuation(anyLong());
    }

    @Test
    void availableEmptyStoreReturnsAnEmptyPage() {
        when(reader.findByPropertyId(7L, PageRequest.of(0, 20))).thenReturn(Page.empty(PageRequest.of(0, 20)));
        var result = service().getHistory(7L, 0, 20);
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        verifyNoInteractions(valuations);
    }

    @Test
    void storageFailureIsNotMisrepresentedAsEmptyHistory() {
        when(reader.findByPropertyId(7L, PageRequest.of(0, 20)))
            .thenThrow(new DataAccessResourceFailureException("History store unavailable"));
        assertThatThrownBy(() -> service().getHistory(7L, 0, 20))
            .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(valuations);
    }

    @Test
    void adapterCannotReturnAnotherPropertysSnapshot() {
        var wrong = new PropertyValuePrediction();
        wrong.setPropertyId(8L);
        when(reader.findByPropertyId(7L, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(new ValuationHistorySnapshot(wrong, Instant.now(), null))));
        assertThatThrownBy(() -> service().getHistory(7L, 0, 20)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(valuations);
    }

    @Test
    void frozenModelEvidenceAndSeededProvenanceAreDisplayedWithoutLiveSales() {
        var prediction = new PropertyValuePrediction();
        prediction.setPropertyId(7L);
        var snapshot = new ValuationHistorySnapshot(prediction, Instant.now(), null, 12L,
            "SEEDED_CURRENT", null, Instant.parse("2026-11-08T00:00:00Z"), "a".repeat(64),
            """
            {"last_sale":{"price":695000.123456,"date":"2026-05-06"},
             "nearby_sales":{"median_price":553500,"count":24,"price_per_sqft":355.78,
               "radius_deg":0.005,"lookback_days":730,"window_end":"2026-11-08"}}
            """, null);
        var display = new ValuationResponse("limited", List.of(), null, null, null, null, null, null, List.of());
        when(reader.findByPropertyId(7L,PageRequest.of(0,20))).thenReturn(new PageImpl<>(List.of(snapshot)));
        when(valuations.explainSnapshot(prediction,null)).thenReturn(display);
        var entry = service().getHistory(7L,0,20).content().get(0);
        assertThat(entry.provenanceLabel()).isEqualTo("recorded before history");
        assertThat(entry.scoredAt()).isNull();
        assertThat(entry.run()).isNull();
        assertThat(entry.modelEvidence().lastSale().price()).isEqualByComparingTo("695000.123456");
        assertThat(entry.modelEvidence().nearbySales().radiusDegrees()).isEqualByComparingTo("0.005");
        verify(valuations,never()).getValuation(anyLong());
    }

    @Test
    void unsupportedHistorySuppressesFrozenModelEvidenceToo() {
        var prediction = new PropertyValuePrediction(); prediction.setPropertyId(7L);
        var snapshot = new ValuationHistorySnapshot(prediction, Instant.now(), null, 1L,"PUBLISHED",null,
            Instant.now(),"a".repeat(64),"{\"last_sale\":{\"price\":100}}",null);
        when(reader.findByPropertyId(7L,PageRequest.of(0,20))).thenReturn(new PageImpl<>(List.of(snapshot)));
        when(valuations.explainSnapshot(prediction,null)).thenReturn(
            new ValuationResponse("unsupported", List.of(), null, null, null, null, null, null, List.of()));
        assertThat(service().getHistory(7L,0,20).content().get(0).modelEvidence()).isNull();
    }

    private ValuationHistoryServiceImpl service() {
        return new ValuationHistoryServiceImpl(properties, reader, valuations);
    }
}
