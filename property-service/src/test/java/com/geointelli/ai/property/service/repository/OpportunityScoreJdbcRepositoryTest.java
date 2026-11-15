package com.geointelli.ai.property.service.repository;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.dto.OpportunityScoreDTO;
import com.geointelli.ai.property.service.dto.OpportunitySignals;
import com.geointelli.ai.property.service.repository.OpportunityScoreJdbcRepository.ScoredProperty;
import com.geointelli.ai.property.service.service.OpportunityScoringService;
import com.geointelli.ai.property.service.service.impl.OpportunityScoringServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** The parameters the batch upsert binds, without a database (the SQL itself is covered by the PostgreSQL test). */
class OpportunityScoreJdbcRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final OpportunityScoreJdbcRepository repository = new OpportunityScoreJdbcRepository(jdbc,
            JsonMapper.builder().addModule(new JavaTimeModule()).build());
    private final OpportunityScoringService scoring = new OpportunityScoringServiceImpl(repository);

    private ScoredProperty scored(OpportunitySignals signals) {
        return new ScoredProperty(signals, scoring.score(signals, NOW));
    }

    private SqlParameterSource[] upserted(List<ScoredProperty> rows) {
        repository.upsert(rows);
        ArgumentCaptor<SqlParameterSource[]> batch = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbc).batchUpdate(anyString(), batch.capture());
        return batch.getValue();
    }

    @Test
    @DisplayName("A valuation without forecasts is stored as UNAVAILABLE with no growth, next to a scored property")
    void valuationWithoutForecast_isStoredWithoutGrowth() {
        // Like the 3,810 local valuations without forecasts: an estimate but no forecast_1y, forecast_3y or outlook
        OpportunitySignals withoutForecast = new OpportunitySignals(5756L, 1L, 10L, true, "limited",
                new BigDecimal("4540672.22"), null, null, null, "medium", 70, "v17+v18", LocalDate.of(2026, 7, 26),
                "run-1");
        OpportunitySignals complete = new OpportunitySignals(95340L, 1L, 10L, true, "supported",
                new BigDecimal("300000"), new BigDecimal("312000"), new BigDecimal("330000"), -0.17, "high", 85,
                "v17+v18", LocalDate.of(2026, 7, 26), "run-1");

        SqlParameterSource[] batch = upserted(List.of(scored(withoutForecast), scored(complete)));

        assertEquals(2, batch.length);
        SqlParameterSource unavailable = batch[0];
        assertEquals(5756L, unavailable.getValue("propertyId"));
        assertEquals(OpportunityScoreDTO.STATUS_UNAVAILABLE, unavailable.getValue("status"));
        assertNull(unavailable.getValue("score"));
        assertNull(unavailable.getValue("growth1y"), "an unavailable growth factor is stored as no growth, not 0");
        assertEquals(0, new BigDecimal("4540672.22").compareTo((BigDecimal) unavailable.getValue("estimatedValue")));
        assertTrue(((String) unavailable.getValue("reasons")).contains("NO_FORECAST"));

        SqlParameterSource available = batch[1];
        assertEquals(OpportunityScoreDTO.STATUS_SCORED, available.getValue("status"));
        assertEquals(new BigDecimal("4.00"), available.getValue("growth1y"));
        assertEquals("run-1", available.getValue("runId"));
        assertEquals(OffsetDateTime.parse("2026-11-15T10:00:00Z"), available.getValue("scoredAt"));
    }

    @Test
    @DisplayName("A property with a valuation that is unsupported, without estimate or forecast, is stored without basis values")
    void unsupportedValuation_isStoredWithoutGrowth() {
        OpportunitySignals unsupported = new OpportunitySignals(2L, null, null, true, "unsupported", null, null, null,
                null, "low", 40, "v17+v18", LocalDate.of(2026, 7, 26), "run-1");

        SqlParameterSource row = upserted(List.of(scored(unsupported)))[0];

        assertEquals(OpportunityScoreDTO.STATUS_UNAVAILABLE, row.getValue("status"));
        assertNull(row.getValue("growth1y"));
        assertNull(row.getValue("countyId"));
        assertTrue(((String) row.getValue("factors")).startsWith("["));
    }

    @Test
    @DisplayName("An empty batch issues no statement")
    void emptyBatch_isSkipped() {
        repository.upsert(List.of());

        verifyNoInteractions(jdbc);
    }
}
