package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.dto.EconomicIndicatorDTO;
import com.geointelli.ai.property.service.dto.EconomicIndicatorObservationDTO;
import com.geointelli.ai.property.service.dto.EconomicIndicatorObservationsDTO;
import com.geointelli.ai.property.service.repository.EconomicIndicatorJdbcRepository;

/** Reading of the stored economic indicators (SCRUM-133), with the repository mocked. */
class EconomicIndicatorServiceImplTest {

    private static final EconomicIndicatorDTO.Coverage NONE =
            new EconomicIndicatorDTO.Coverage(0, 0, 0, null, null, null, null);
    private static final EconomicIndicatorDTO POPULATION = new EconomicIndicatorDTO("POPULATION",
            "Resident population", "Resident population on July 1 of the year.", "PERSONS", "ANNUAL", "COUNTY",
            "CENSUS_PEP", "U.S. Census Bureau, Population Estimates Program",
            "https://www.census.gov/programs-surveys/popest.html", NONE);
    private static final EconomicIndicatorDTO UNEMPLOYMENT_RATE = new EconomicIndicatorDTO("UNEMPLOYMENT_RATE",
            "Unemployment rate", "Unemployed people as a percent of the labor force.", "PERCENT", "MONTHLY", "COUNTY",
            "BLS_LAUS", "U.S. Bureau of Labor Statistics, Local Area Unemployment Statistics",
            "https://www.bls.gov/lau/", NONE);

    private EconomicIndicatorJdbcRepository repository;
    private EconomicIndicatorServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(EconomicIndicatorJdbcRepository.class);
        service = new EconomicIndicatorServiceImpl(repository);
        when(repository.findIndicator("POPULATION")).thenReturn(Optional.of(POPULATION));
        when(repository.findIndicator("UNEMPLOYMENT_RATE")).thenReturn(Optional.of(UNEMPLOYMENT_RATE));
        when(repository.findIndicator("GDP")).thenReturn(Optional.empty());
    }

    private static EconomicIndicatorObservationDTO population2024() {
        Instant imported = Instant.parse("2026-11-25T09:00:00Z");
        return new EconomicIndicatorObservationDTO("12086", "Miami-Dade County", "2024", LocalDate.of(2024, 7, 1),
                LocalDate.of(2024, 7, 1), new BigDecimal("2812144.00"), "ESTIMATE", "POPESTIMATE2024", "2025",
                "https://www2.census.gov/.../co-est2025-alldata.csv", new BigDecimal("2838461.00"), 1, imported,
                imported, imported);
    }

    @Test
    @DisplayName("When listing, then the indicators of the repository are returned as they are")
    void whenList_thenRepositoryIndicators() {
        List<EconomicIndicatorDTO> indicators = List.of(UNEMPLOYMENT_RATE, POPULATION);
        when(repository.findIndicators()).thenReturn(indicators);

        assertSame(indicators, service.getIndicators());
    }

    @Test
    @DisplayName("When values are stored, then status OK with the values, unit and frequency of the indicator")
    void whenStored_thenOk() {
        when(repository.findObservations("POPULATION", "12086", false, LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31))).thenReturn(List.of(population2024()));

        EconomicIndicatorObservationsDTO result = service.getObservations("POPULATION", "12086", 2024, 2024);

        assertEquals(EconomicIndicatorObservationsDTO.STATUS_OK, result.status());
        assertEquals("POPULATION", result.indicatorCode());
        assertEquals("PERSONS", result.unit());
        assertEquals("ANNUAL", result.frequency());
        assertEquals("12086", result.geographyCode());
        assertEquals(2024, result.fromYear());
        assertEquals(2024, result.toYear());
        assertEquals(List.of(population2024()), result.observations());
    }

    @Test
    @DisplayName("When nothing is stored for the county, then status EMPTY with an empty list, not an error")
    void whenNothingStored_thenEmpty() {
        when(repository.findObservations(anyString(), anyString(), anyBoolean(), any(), any())).thenReturn(List.of());

        EconomicIndicatorObservationsDTO result = service.getObservations("POPULATION", "12999", null, null);

        assertEquals(EconomicIndicatorObservationsDTO.STATUS_EMPTY, result.status());
        assertTrue(result.observations().isEmpty());
    }

    @Test
    @DisplayName("When the code is in lower case, then it is found; a monthly indicator asks for monthly labels")
    void whenLowerCase_thenFoundAndMonthly() {
        when(repository.findObservations(anyString(), anyString(), anyBoolean(), any(), any())).thenReturn(List.of());

        EconomicIndicatorObservationsDTO result = service.getObservations(" unemployment_rate ", "12086", null, null);

        assertEquals("UNEMPLOYMENT_RATE", result.indicatorCode());
        verify(repository).findObservations("UNEMPLOYMENT_RATE", "12086", true, null, null);
    }

    @Test
    @DisplayName("When the indicator is unknown, then 404")
    void whenUnknownCode_thenNotFound() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getObservations("GDP", "12086", null, null));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals("Unknown economic indicator: GDP", error.getReason());
    }

    @Test
    @DisplayName("When the fips is not 5 digits or the years are reversed, then 400 before reading the database")
    void whenInvalidInput_thenBadRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.getObservations("POPULATION", "abc", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getObservations("POPULATION", null, null, null));
        assertEquals("fromYear must not be after toYear", assertThrows(IllegalArgumentException.class,
                () -> service.getObservations("POPULATION", "12086", 2025, 2024)).getMessage());
        verifyNoInteractions(repository);
    }
}
