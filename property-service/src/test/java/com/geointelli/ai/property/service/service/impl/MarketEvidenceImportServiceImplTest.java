package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.MarketEvidenceObservation;
import com.geointelli.ai.property.service.entity.State;
import com.geointelli.ai.property.service.repository.CountyRepository;
import com.geointelli.ai.property.service.repository.MarketEvidenceObservationRepository;
import com.geointelli.ai.property.service.repository.StateRepository;

/** Row rules of the market evidence import (SCRUM-131), with the repositories mocked. */
class MarketEvidenceImportServiceImplTest {
    private static final List<String> COLUMNS = List.of("source", "evidence_type", "geography_level", "geography_code",
        "geography_name", "state_code", "county_name", "period_start", "period_end", "source_dataset", "currency",
        "sale_count", "median_sale_price", "active_listing_count", "median_days_on_market");

    private MarketEvidenceObservationRepository observations;
    private StateRepository states;
    private CountyRepository counties;
    private Consumer<CSVRecord> importer;
    private UUID jobId;
    private State florida;
    private County miamiDade;

    @BeforeEach
    void setUp() {
        observations = mock(MarketEvidenceObservationRepository.class);
        states = mock(StateRepository.class);
        counties = mock(CountyRepository.class);
        florida = new State();
        florida.setId(10L);
        florida.setCode("FL");
        miamiDade = new County();
        miamiDade.setId(1L);
        miamiDade.setName("Miami-Dade");
        miamiDade.setState(florida);
        when(states.findByCodeIgnoreCase("FL")).thenReturn(Optional.of(florida));
        when(counties.findByCountyAndState("Miami-Dade", "FL")).thenReturn(Optional.of(miamiDade));
        when(counties.findIdByFipsCode("12086")).thenReturn(Optional.of(1L));
        when(counties.findByIdIn(List.of(1L))).thenReturn(List.of(miamiDade));
        jobId = UUID.randomUUID();
        importer = new MarketEvidenceImportServiceImpl(observations, states, counties).rowImporter(jobId);
    }

    /** A SALE row of ZIP 33139 in Miami-Dade for October 2026, 42 sales. */
    private static Map<String, String> saleRow() {
        Map<String, String> row = new HashMap<>();
        row.put("source", "sample_provider");
        row.put("evidence_type", "SALE");
        row.put("geography_level", "ZIP");
        row.put("geography_code", "33139");
        row.put("state_code", "FL");
        row.put("county_name", "Miami-Dade");
        row.put("period_start", "2026-10-01");
        row.put("period_end", "2026-10-31");
        row.put("sale_count", "42");
        return row;
    }

    private static Map<String, String> askingRow() {
        Map<String, String> row = saleRow();
        row.put("evidence_type", "ASKING");
        row.remove("sale_count");
        row.put("active_listing_count", "318");
        return row;
    }

    private static CSVRecord record(Map<String, String> values) throws IOException {
        String line = COLUMNS.stream().map(column -> quote(values.getOrDefault(column, ""))).collect(Collectors.joining(","));
        return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
            .parse(new StringReader(String.join(",", COLUMNS) + "\n" + line + "\n")).getRecords().get(0);
    }

    private static String quote(String value) {
        return value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }

    private MarketEvidenceObservation imported(Map<String, String> values) throws IOException {
        importer.accept(record(values));
        ArgumentCaptor<MarketEvidenceObservation> saved = ArgumentCaptor.forClass(MarketEvidenceObservation.class);
        verify(observations).save(saved.capture());
        return saved.getValue();
    }

    private String failure(Map<String, String> values) throws IOException {
        CSVRecord record = record(values);
        String message = assertThrows(IllegalArgumentException.class, () -> importer.accept(record)).getMessage();
        verify(observations, never()).save(any());
        return message;
    }

    @Nested
    @DisplayName("Linking the row to our states and counties")
    class Linking {

        @Test
        @DisplayName("When a ZIP row names a configured state and county, then it is linked to both")
        void whenZipRowConfigured_thenLinked() throws Exception {
            Map<String, String> values = saleRow();
            values.put("median_sale_price", "$627,500");

            MarketEvidenceObservation row = imported(values);

            assertEquals("SAMPLE_PROVIDER", row.getSource());
            assertEquals(0, new BigDecimal("627500").compareTo(row.getMedianSalePrice()));
            assertSame(florida, row.getState());
            assertSame(miamiDade, row.getCounty());
            assertEquals(jobId.toString(), row.getImportJobId());
            assertEquals("USD", row.getCurrency());
        }

        @Test
        @DisplayName("When the state is a valid U.S. state we have not configured (GA), then the row is imported unlinked")
        void whenStateNotConfigured_thenImportedUnlinked() throws Exception {
            Map<String, String> values = saleRow();
            values.put("state_code", "ga");
            values.put("county_name", "Fulton");

            MarketEvidenceObservation row = imported(values);

            assertNull(row.getState());
            assertNull(row.getCounty());
        }

        @Test
        @DisplayName("When a STATE row is a valid state we have not configured, then it is imported with its code")
        void whenStateRowNotConfigured_thenImported() throws Exception {
            Map<String, String> values = askingRow();
            values.put("geography_level", "STATE");
            values.put("geography_code", "ga");
            values.remove("state_code");
            values.remove("county_name");

            MarketEvidenceObservation row = imported(values);

            assertEquals("GA", row.getGeographyCode());
            assertNull(row.getState());
        }

        @Test
        @DisplayName("When the code is not a U.S. state, then the row fails naming the column")
        void whenNotAUsState_thenRowFails() throws Exception {
            Map<String, String> zip = saleRow();
            zip.put("state_code", "ZZ");
            assertTrue(failure(zip).startsWith("state_code: not a U.S. state code, got 'ZZ'"));

            Map<String, String> state = askingRow();
            state.put("geography_level", "STATE");
            state.put("geography_code", "ZZ");
            assertTrue(failure(state).startsWith("geography_code: not a U.S. state code"));
        }

        @Test
        @DisplayName("When a COUNTY row has the FIPS code of a configured county, then it is linked by that code and takes its state")
        void whenCountyRowFips_thenLinkedByFips() throws Exception {
            Map<String, String> values = saleRow();
            values.put("geography_level", "COUNTY");
            values.put("geography_code", "12086");
            values.put("geography_name", "Dade (provider spelling)");
            values.remove("state_code");
            values.remove("county_name");

            MarketEvidenceObservation row = imported(values);

            assertSame(miamiDade, row.getCounty());
            assertSame(florida, row.getState());
            verify(counties, never()).findByCountyAndState(anyString(), anyString());
        }

        @Test
        @DisplayName("When a COUNTY row's state_code is not the state of its FIPS county, then the row fails")
        void whenCountyRowStateDiffers_thenRowFails() throws Exception {
            Map<String, String> values = saleRow();
            values.put("geography_level", "COUNTY");
            values.put("geography_code", "12086");
            values.put("state_code", "GA");

            assertEquals("state_code: 'GA' is not the state of county 12086 (FL)", failure(values));
        }

        @Test
        @DisplayName("When no county has the FIPS code, then the COUNTY row is imported unlinked, never by name")
        void whenFipsUnknown_thenUnlinked() throws Exception {
            Map<String, String> values = saleRow();
            values.put("geography_level", "COUNTY");
            values.put("geography_code", "12999");
            values.put("geography_name", "Miami-Dade");

            MarketEvidenceObservation row = imported(values);

            assertNull(row.getCounty());
            assertSame(florida, row.getState());
            verify(counties, never()).findByCountyAndState(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("The same observation twice")
    class Duplicates {

        @Test
        @DisplayName("When this job already wrote the observation, then the second row fails instead of overwriting the first")
        void whenSameJobWroteIt_thenRowFails() throws Exception {
            MarketEvidenceObservation earlier = new MarketEvidenceObservation();
            earlier.setImportJobId(jobId.toString());
            when(observations.findObservation(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(earlier));

            assertTrue(failure(saleRow()).startsWith("the file has this observation twice"));
        }

        @Test
        @DisplayName("When an earlier import wrote the observation, then the row updates it in place")
        void whenEarlierImportWroteIt_thenUpdated() throws Exception {
            MarketEvidenceObservation earlier = new MarketEvidenceObservation();
            earlier.setId(7L);
            earlier.setImportJobId(UUID.randomUUID().toString());
            when(observations.findObservation(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of(earlier));

            MarketEvidenceObservation row = imported(saleRow());

            assertSame(earlier, row);
            assertEquals(jobId.toString(), row.getImportJobId());
            assertEquals(42, row.getSaleCount());
        }
    }

    @Nested
    @DisplayName("Values that do not fit their column")
    class Sizes {

        @Test
        @DisplayName("When an amount has more decimals than its column keeps, then the row fails instead of being rounded")
        void whenTooManyDecimals_thenRowFails() throws Exception {
            Map<String, String> days = askingRow();
            days.put("median_days_on_market", "61.25");
            assertEquals("median_days_on_market: at most 1 decimal, got '61.25'", failure(days));

            Map<String, String> price = saleRow();
            price.put("median_sale_price", "1000.125");
            assertEquals("median_sale_price: at most 2 decimals, got '1000.125'", failure(price));
        }

        @Test
        @DisplayName("When the decimals fit once trailing zeros are dropped, then the value is kept")
        void whenTrailingZeros_thenKept() throws Exception {
            Map<String, String> values = askingRow();
            values.put("median_days_on_market", "61.20");

            assertEquals(0, new BigDecimal("61.2").compareTo(imported(values).getMedianDaysOnMarket()));
        }

        @Test
        @DisplayName("When an amount has more whole digits than its column, then the row fails; the largest value fits")
        void whenTooManyDigits_thenRowFails() throws Exception {
            Map<String, String> tooBig = saleRow();
            tooBig.put("median_sale_price", "12345678901234");
            assertEquals("median_sale_price: at most 12 digits before the decimal point, got '12345678901234'",
                failure(tooBig));

            Map<String, String> largest = saleRow();
            largest.put("median_sale_price", "999,999,999,999.99");
            assertEquals(0, new BigDecimal("999999999999.99").compareTo(imported(largest).getMedianSalePrice()));
        }

        @Test
        @DisplayName("When a count does not fit an integer, then the row fails naming the limit")
        void whenCountTooBig_thenRowFails() throws Exception {
            Map<String, String> values = saleRow();
            values.put("sale_count", "3000000000");

            assertTrue(failure(values).startsWith("sale_count: expected a whole number up to 2147483647"));
        }

        @Test
        @DisplayName("When a text is longer than its column, then the row fails with the length, not the text")
        void whenTextTooLong_thenRowFails() throws Exception {
            Map<String, String> name = saleRow();
            name.put("geography_name", "x".repeat(121));
            assertEquals("geography_name: at most 120 characters, got 121", failure(name));

            Map<String, String> dataset = saleRow();
            dataset.put("source_dataset", "x".repeat(129));
            assertEquals("source_dataset: at most 128 characters, got 129", failure(dataset));
        }

        @Test
        @DisplayName("When a bad value is long, then the message quotes only its start, so it fits the job's error column")
        void whenBadValueLong_thenQuotedShort() throws Exception {
            Map<String, String> values = saleRow();
            values.put("sale_count", "9".repeat(20) + "x".repeat(500));

            String message = failure(values);

            assertTrue(message.length() < 150, message);
            assertTrue(message.endsWith("...'"), message);
        }

        @Test
        @DisplayName("When the currency is not three letters, then the row fails; lower case is stored upper case")
        void whenCurrency_thenChecked() throws Exception {
            Map<String, String> bad = saleRow();
            bad.put("currency", "US");
            assertEquals("currency: use a three-letter code, got 'US'", failure(bad));

            Map<String, String> lower = saleRow();
            lower.put("currency", "usd");
            assertEquals("USD", imported(lower).getCurrency());
        }
    }
}
