package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.EconomicObservation;

/** Reading of a BLS LAUS API answer (SCRUM-133), without calling the API. */
class BlsLausProviderServiceImplTest {

    private static final Map<String, String> COUNTIES = Map.of("12086", "Miami-Dade");

    /** A real answer shape: Miami-Dade unemployment rate, with an annual average, a missing and a preliminary month. */
    private static final String ANSWER = """
            {"status": "REQUEST_SUCCEEDED", "message": ["No Data Available for Series LAUCN120860000000006 Year: 2027"],
             "Results": {"series": [{"seriesID": "LAUCN120860000000003", "data": [
               {"year": "2026", "period": "M10", "value": "1.4", "footnotes": [{"code": "P", "text": "Preliminary."}]},
               {"year": "2026", "period": "M09", "value": "1.9", "footnotes": [{}]},
               {"year": "2025", "period": "M13", "value": "2.7", "footnotes": [{"code": "G"}]},
               {"year": "2025", "period": "M10", "value": "-", "footnotes": [{"code": "X"}]}
             ]}, {"seriesID": "LAUCN120860000000005", "data": [
               {"year": "2026", "period": "M09", "value": "1,412,345", "footnotes": [{}]}
             ]}]}}
            """;

    private final List<EconomicObservation> observations = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();

    private int read(String json) throws Exception {
        JsonNode root = new ObjectMapper().readTree(json);
        return BlsLausProviderServiceImpl.read(root, COUNTIES, observations, messages);
    }

    @Test
    @DisplayName("When the answer has months, then each month is one ESTIMATE value of the indicator of its series")
    void whenMonths_thenOneEstimatePerMonth() throws Exception {
        read(ANSWER);

        assertEquals(3, observations.size());
        EconomicObservation september = observations.get(1);
        assertEquals("UNEMPLOYMENT_RATE", september.indicatorCode());
        assertEquals("12086", september.geographyCode());
        assertEquals("Miami-Dade", september.geographyName());
        assertEquals(LocalDate.of(2026, 9, 1), september.periodStart());
        assertEquals(LocalDate.of(2026, 9, 30), september.periodEnd());
        assertEquals(new BigDecimal("1.9"), september.value());
        assertEquals(EconomicObservation.ESTIMATE, september.observationKind());
        assertEquals("LAUCN120860000000003", september.sourceSeriesId());
    }

    @Test
    @DisplayName("When a month is preliminary (footnote P), then it is an ESTIMATE like the others")
    void whenPreliminary_thenEstimate() throws Exception {
        read(ANSWER);

        assertEquals(LocalDate.of(2026, 10, 1), observations.get(0).periodStart());
        assertEquals(EconomicObservation.ESTIMATE, observations.get(0).observationKind());
    }

    @Test
    @DisplayName("When the answer has an annual average (M13), then it is skipped: monthly and annual are not mixed")
    void whenAnnualAverage_thenSkipped() throws Exception {
        read(ANSWER);

        assertTrue(observations.stream().allMatch(o -> o.periodStart().getDayOfMonth() == 1
                && o.periodEnd().equals(o.periodStart().withDayOfMonth(o.periodStart().lengthOfMonth()))));
        assertTrue(observations.stream().noneMatch(o -> o.periodStart().equals(LocalDate.of(2025, 1, 1))));
    }

    @Test
    @DisplayName("When a value is \"-\" (October 2025, shutdown), then it is counted as missing and not returned")
    void whenDash_thenMissingNotZero() throws Exception {
        int missing = read(ANSWER);

        assertEquals(1, missing);
        assertTrue(observations.stream().noneMatch(o -> o.periodStart().equals(LocalDate.of(2025, 10, 1))));
    }

    @Test
    @DisplayName("When a value has thousands separators, then it is read as a number; the measure 05 is EMPLOYMENT")
    void whenThousandsSeparator_thenNumber() throws Exception {
        read(ANSWER);

        EconomicObservation employment = observations.get(2);
        assertEquals("EMPLOYMENT", employment.indicatorCode());
        assertEquals(new BigDecimal("1412345"), employment.value());
    }

    @Test
    @DisplayName("When the API sends messages, then they are kept for the import result")
    void whenMessages_thenKept() throws Exception {
        read(ANSWER);

        assertEquals(List.of("No Data Available for Series LAUCN120860000000006 Year: 2027"), messages);
    }

    @Test
    @DisplayName("When the answer has no series, then nothing is returned and nothing is missing")
    void whenNoSeries_thenEmpty() throws Exception {
        int missing = read("{\"status\": \"REQUEST_SUCCEEDED\", \"message\": [], \"Results\": {\"series\": []}}");

        assertEquals(0, missing);
        assertTrue(observations.isEmpty());
    }
}
