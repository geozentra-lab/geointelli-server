package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.dto.EconomicObservation;

/** Reading of a Census county population file (SCRUM-133), without downloading it. */
class CensusPopulationProviderServiceImplTest {

    private static final String URL = "https://www2.census.gov/.../co-est2021-alldata.csv";
    private static final String HEADER = "SUMLEV,STATE,COUNTY,STNAME,CTYNAME,POPESTIMATE2020,POPESTIMATE2021,"
            + "NETMIG2020,NETMIG2021,DOMESTICMIG2020,DOMESTICMIG2021,INTERNATIONALMIG2020,INTERNATIONALMIG2021\n";
    /** Vintage 2021: real Miami-Dade figures; a state row, a Georgia county and a New Mexico county to skip. */
    private static final String FILE = HEADER
            + "040,12,000,Florida,Florida,21592035,21831949,65442,293424,62600,246571,2842,46853\n"
            + "050,12,086,Florida,Miami-Dade County,2695729,2669925,-6495,-26051,-7235,-41159,740,15108\n"
            + "050,13,121,Georgia,Fulton County,1066710,1065334,1000,2000,500,1500,500,500\n"
            + "050,35,013,New Mexico,Doña Ana County,219561,221508,100,200,50,150,50,50\n";

    private final List<EconomicObservation> observations = new ArrayList<>();

    private int read(String csv, String stateFips) {
        return CensusPopulationProviderServiceImpl.read(csv.getBytes(StandardCharsets.ISO_8859_1), stateFips, 2021,
                URL, observations);
    }

    private EconomicObservation find(String indicatorCode, int year) {
        return observations.stream()
                .filter(o -> o.indicatorCode().equals(indicatorCode) && o.periodEnd().getYear() == year)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("When reading Florida, then only its counties (SUMLEV 050) are kept, not the state row nor Georgia")
    void whenState_thenOnlyItsCounties() {
        read(FILE, "12");

        assertTrue(observations.stream().allMatch(o -> o.geographyCode().equals("12086")));
        assertEquals("Miami-Dade County", observations.get(0).geographyName());
    }

    @Test
    @DisplayName("When reading vintage 2021, then 2 populations and the 3 migrations of 2021 only (2020 is partial)")
    void whenVintage_thenPopulationsAndFullYearMigration() {
        read(FILE, "12");

        assertEquals(5, observations.size());
        assertEquals(new BigDecimal("2695729"), find("POPULATION", 2020).value());
        assertEquals(new BigDecimal("2669925"), find("POPULATION", 2021).value());
        assertEquals(new BigDecimal("-26051"), find("NET_MIGRATION", 2021).value());
        assertEquals(new BigDecimal("-41159"), find("NET_DOMESTIC_MIGRATION", 2021).value());
        assertEquals(new BigDecimal("15108"), find("NET_INTERNATIONAL_MIGRATION", 2021).value());
        assertTrue(observations.stream().noneMatch(o -> o.indicatorCode().startsWith("NET_")
                && o.periodEnd().getYear() == 2020));
    }

    @Test
    @DisplayName("When a value is read, then its period, source column and ESTIMATE kind are set")
    void whenRead_thenPeriodSourceAndKind() {
        read(FILE, "12");

        EconomicObservation population = find("POPULATION", 2021);
        assertEquals(LocalDate.of(2021, 7, 1), population.periodStart());
        assertEquals(LocalDate.of(2021, 7, 1), population.periodEnd());
        assertEquals("POPESTIMATE2021", population.sourceSeriesId());
        assertEquals(EconomicObservation.ESTIMATE, population.observationKind());

        EconomicObservation migration = find("NET_MIGRATION", 2021);
        assertEquals(LocalDate.of(2020, 7, 1), migration.periodStart());
        assertEquals(LocalDate.of(2021, 6, 30), migration.periodEnd());
    }

    @Test
    @DisplayName("When a name has a Latin-1 letter, then it is read correctly")
    void whenLatin1_thenNameKept() {
        read(FILE, "35");

        assertEquals("35013", observations.get(0).geographyCode());
        assertEquals("Doña Ana County", observations.get(0).geographyName());
    }

    @Test
    @DisplayName("When a cell is empty, then it is counted as missing and not returned")
    void whenEmptyCell_thenMissing() {
        int missing = read(HEADER + "050,12,086,Florida,Miami-Dade County,2695729,,-6495,-26051,-7235,-41159,740,15108\n",
                "12");

        assertEquals(1, missing);
        assertEquals(4, observations.size());
        assertTrue(observations.stream().noneMatch(o -> o.indicatorCode().equals("POPULATION")
                && o.periodEnd().getYear() == 2021));
    }

    @Test
    @DisplayName("When a column of the vintage is missing, then 502: the file is not the expected one")
    void whenColumnMissing_thenBadGateway() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> read("SUMLEV,STATE,COUNTY,CTYNAME,POPESTIMATE2021\n050,12,086,Miami-Dade County,1\n", "12"));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertTrue(error.getReason().contains("NETMIG2021"));
    }
}
