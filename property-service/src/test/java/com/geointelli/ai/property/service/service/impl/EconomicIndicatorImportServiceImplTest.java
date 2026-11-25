package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.dto.EconomicIndicatorImportResultDTO;
import com.geointelli.ai.property.service.dto.EconomicObservation;
import com.geointelli.ai.property.service.dto.EconomicProviderData;
import com.geointelli.ai.property.service.repository.EconomicIndicatorJdbcRepository;
import com.geointelli.ai.property.service.repository.EconomicIndicatorJdbcRepository.RunCounts;
import com.geointelli.ai.property.service.service.BlsLausProviderService;
import com.geointelli.ai.property.service.service.CensusPopulationProviderService;
import com.geointelli.ai.property.service.service.ReferenceDataScopeService;

/** Validation and counts of the economic indicator imports (SCRUM-133), with the providers and database mocked. */
class EconomicIndicatorImportServiceImplTest {

    private static final int CURRENT_YEAR = LocalDate.now(ZoneOffset.UTC).getYear();
    private static final String CENSUS_URL = "https://www2.census.gov/.../co-est2025-alldata.csv";

    private EconomicIndicatorJdbcRepository repository;
    private ReferenceDataScopeService scopes;
    private BlsLausProviderService blsLaus;
    private CensusPopulationProviderService censusPopulation;
    private PlatformTransactionManager transactionManager;
    private EconomicIndicatorImportServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(EconomicIndicatorJdbcRepository.class);
        scopes = mock(ReferenceDataScopeService.class);
        blsLaus = mock(BlsLausProviderService.class);
        censusPopulation = mock(CensusPopulationProviderService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        service = new EconomicIndicatorImportServiceImpl(repository, scopes, blsLaus, censusPopulation,
                transactionManager);

        when(scopes.hasTable("economic_indicator_observations")).thenReturn(true);
        when(scopes.stateFips("FL")).thenReturn(Optional.of("12"));
        Map<String, String> florida = new LinkedHashMap<>();
        florida.put("12011", "Broward");
        florida.put("12086", "Miami-Dade");
        when(repository.findCounties("12")).thenReturn(florida);
    }

    private static EconomicObservation population(String fips, int year, String value) {
        LocalDate july1 = LocalDate.of(year, 7, 1);
        return new EconomicObservation("POPULATION", fips, "County " + fips, july1, july1, new BigDecimal(value),
                EconomicObservation.ESTIMATE, "POPESTIMATE" + year);
    }

    private static EconomicProviderData census(List<EconomicObservation> observations) {
        return new EconomicProviderData(CENSUS_URL, "2025", observations, 2, List.of());
    }

    @Test
    @DisplayName("When the state is missing, then 400 before anything is read")
    void whenStateMissing_thenBadRequest() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.importBlsLaus(" ", null, null, null, "admin"));

        assertEquals("state is required, for example FL", error.getMessage());
        verifyNoInteractions(blsLaus, repository);
    }

    @Test
    @DisplayName("When migration V51 is not applied, then 409 and no provider is called")
    void whenTableMissing_thenConflict() {
        when(scopes.hasTable("economic_indicator_observations")).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.importCensusPopulation("FL", 2025, "admin"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("apply migration V51 first"));
        verifyNoInteractions(censusPopulation);
    }

    @Test
    @DisplayName("When the state is unknown, then 400")
    void whenStateUnknown_thenBadRequest() {
        when(scopes.stateFips("ZZ")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.importBlsLaus("ZZ", null, null, null, "admin"));
        verifyNoInteractions(blsLaus);
    }

    @Test
    @DisplayName("When the BLS years break a rule, then 400 and the API is not called")
    void whenYearsInvalid_thenBadRequest() {
        assertEquals("fromYear must be 1990 or later", assertThrows(IllegalArgumentException.class,
                () -> service.importBlsLaus("FL", null, 1989, 1990, "admin")).getMessage());
        assertEquals("toYear must not be after " + CURRENT_YEAR, assertThrows(IllegalArgumentException.class,
                () -> service.importBlsLaus("FL", null, null, CURRENT_YEAR + 1, "admin")).getMessage());
        assertEquals("fromYear must not be after toYear", assertThrows(IllegalArgumentException.class,
                () -> service.importBlsLaus("FL", null, 2025, 2024, "admin")).getMessage());
        assertEquals("The period must not be longer than 10 years", assertThrows(IllegalArgumentException.class,
                () -> service.importBlsLaus("FL", null, 2010, 2020, "admin")).getMessage());
        verifyNoInteractions(blsLaus);
    }

    @Test
    @DisplayName("When the fips is not 5 digits or not a county of the state, then 400")
    void whenFipsInvalid_thenBadRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.importBlsLaus("FL", "123", null, null, "admin"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.importBlsLaus("FL", "13121", null, null, "admin"));

        assertEquals("fips 13121 is not a county of FL", error.getMessage());
        verifyNoInteractions(blsLaus);
    }

    @Test
    @DisplayName("When no year is given, then the BLS import reads the current year and the two before")
    void whenNoYears_thenLastThreeYears() {
        when(blsLaus.fetch(any(), anyInt(), anyInt())).thenReturn(
                new EconomicProviderData("https://api.bls.gov/", "2026-11-25", List.of(), 0, List.of()));
        when(repository.countRun(any(), any())).thenReturn(new RunCounts(0, 0, 0));

        service.importBlsLaus("FL", null, null, null, "admin");

        verify(blsLaus).fetch(Map.of("12011", "Broward", "12086", "Miami-Dade"), CURRENT_YEAR - 2, CURRENT_YEAR);
    }

    @Test
    @DisplayName("When one fips is given, then only that county is requested")
    void whenOneFips_thenOneCounty() {
        when(blsLaus.fetch(any(), anyInt(), anyInt())).thenReturn(
                new EconomicProviderData("https://api.bls.gov/", "2026-11-25", List.of(), 0, List.of()));
        when(repository.countRun(any(), any())).thenReturn(new RunCounts(0, 0, 0));

        EconomicIndicatorImportResultDTO result = service.importBlsLaus("FL", "12086", 2025, 2026, "admin");

        verify(blsLaus).fetch(Map.of("12086", "Miami-Dade"), 2025, 2026);
        assertEquals(1, result.counties());
        assertEquals("BLS_LAUS", result.source());
    }

    @Test
    @DisplayName("When the Census vintage is outside 2021 to the current year, then 400")
    void whenVintageInvalid_thenBadRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.importCensusPopulation("FL", 2020, "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> service.importCensusPopulation("FL", CURRENT_YEAR + 1, "admin"));
        verifyNoInteractions(censusPopulation);
    }

    @Test
    @DisplayName("When no vintage is given, then the previous year is imported")
    void whenNoVintage_thenPreviousYear() {
        when(censusPopulation.fetch(anyString(), anyInt())).thenReturn(census(List.of()));
        when(repository.countRun(any(), any())).thenReturn(new RunCounts(0, 0, 0));

        service.importCensusPopulation("FL", null, "admin");

        verify(censusPopulation).fetch("12", CURRENT_YEAR - 1);
    }

    @Test
    @DisplayName("When values are imported, then unchanged and olderVintage come from what the database wrote")
    void whenImported_thenCounts() {
        List<EconomicObservation> values = List.of(population("12086", 2024, "2812144"),
                population("12086", 2025, "2802029"), population("12011", 2024, "2000000"),
                population("12011", 2025, "2010000"), population("12099", 2025, "1500000"));
        when(censusPopulation.fetch("12", 2025)).thenReturn(census(values));
        // 5 received: 4 written (1 new, 2 revised, so 1 unchanged); 1 not written (a newer vintage is stored)
        when(repository.countRun(any(), any())).thenReturn(new RunCounts(1, 2, 4));

        EconomicIndicatorImportResultDTO result = service.importCensusPopulation("FL", 2025, "admin");

        assertEquals("CENSUS_PEP", result.source());
        assertEquals("12", result.stateFips());
        assertEquals(3, result.counties());
        assertEquals(5, result.received());
        assertEquals(1, result.inserted());
        assertEquals(2, result.revised());
        assertEquals(1, result.unchanged());
        assertEquals(1, result.olderVintage());
        assertEquals(2, result.missingValues());
        assertEquals(LocalDate.of(2024, 7, 1), result.periodFrom());
        assertEquals(LocalDate.of(2025, 7, 1), result.periodTo());
        assertEquals(CENSUS_URL, result.sourceUrl());
        assertEquals("2025", result.sourceVintage());
    }

    @Test
    @DisplayName("When values are imported, then they are written, counted and recorded with the same import time")
    void whenImported_thenSameTimeEverywhere() {
        List<EconomicObservation> values = List.of(population("12086", 2025, "2802029"));
        when(censusPopulation.fetch("12", 2025)).thenReturn(census(values));
        when(repository.countRun(any(), any())).thenReturn(new RunCounts(1, 0, 1));

        EconomicIndicatorImportResultDTO result = service.importCensusPopulation("FL", 2025, "admin");

        ArgumentCaptor<OffsetDateTime> written = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).upsert(eq(values), eq("2025"), eq(CENSUS_URL), written.capture());
        verify(repository).countRun(CensusPopulationProviderService.INDICATORS, written.getValue());
        verify(repository).recordLoad(result, "admin");
        assertEquals(result.importedAt(), written.getValue().toInstant());
        assertEquals(0, result.importedAt().getNano() % 1000, "microseconds, as PostgreSQL stores them");
    }

    @Test
    @DisplayName("When the source gives nothing, then the periods are null and the load is still recorded")
    void whenNothingReceived_thenNullPeriods() {
        when(censusPopulation.fetch("12", 2025)).thenReturn(census(List.of()));
        when(repository.countRun(any(), any())).thenReturn(new RunCounts(0, 0, 0));

        EconomicIndicatorImportResultDTO result = service.importCensusPopulation("FL", 2025, "admin");

        assertEquals(0, result.received());
        assertNull(result.periodFrom());
        assertNull(result.periodTo());
        verify(repository).recordLoad(result, "admin");
    }

    @Test
    @DisplayName("When the source gives the same period twice, then 502 and nothing is written")
    void whenSamePeriodTwice_thenBadGateway() {
        when(censusPopulation.fetch("12", 2025)).thenReturn(census(List.of(population("12086", 2025, "1"),
                population("12086", 2025, "2"))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.importCensusPopulation("FL", 2025, "admin"));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        verify(repository, never()).upsert(any(), any(), any(), any());
        verify(repository, never()).recordLoad(any(), any());
    }

    @Test
    @DisplayName("When the source sends more than 20 messages, then the result keeps the first 20")
    void whenManyMessages_thenTwenty() {
        List<String> messages = IntStream.range(0, 30).mapToObj(i -> "message " + i).toList();
        when(blsLaus.fetch(any(), anyInt(), anyInt())).thenReturn(
                new EconomicProviderData("https://api.bls.gov/", "2026-11-25", List.of(), 0, messages));
        when(repository.countRun(any(), any())).thenReturn(new RunCounts(0, 0, 0));

        EconomicIndicatorImportResultDTO result = service.importBlsLaus("FL", "12086", 2025, 2026, "admin");

        assertEquals(20, result.sourceMessages().size());
        assertEquals("message 0", result.sourceMessages().get(0));
    }
}
