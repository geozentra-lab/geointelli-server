package com.geointelli.ai.property.service.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.geointelli.ai.property.service.dto.EconomicIndicatorDTO;
import com.geointelli.ai.property.service.dto.EconomicIndicatorImportResultDTO;
import com.geointelli.ai.property.service.dto.EconomicIndicatorObservationDTO;
import com.geointelli.ai.property.service.dto.EconomicObservation;
import com.geointelli.ai.property.service.repository.EconomicIndicatorJdbcRepository.RunCounts;

/**
 * Opt-in checks of migration V50 and its SQL (SCRUM-133) on a real PostgreSQL database, with the same rules as
 * MarketEvidenceDatabaseTest: a schema of its own on a dedicated local test database whose name ends with _test,
 * dropped afterwards; never the application's database.
 */
@EnabledIfEnvironmentVariable(named = "ENVIRONMENTAL_TEST_DB_URL", matches = ".+")
class EconomicIndicatorDatabaseTest {

    private static final String SCHEMA = "economic_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String TABLE = "economic_indicator_observations";
    private static final List<String> CENSUS = List.of("POPULATION", "NET_MIGRATION", "NET_DOMESTIC_MIGRATION",
            "NET_INTERNATIONAL_MIGRATION");
    private static final String URL_2024 = "https://www2.census.gov/.../co-est2024-alldata.csv";
    private static final String URL_2025 = "https://www2.census.gov/.../co-est2025-alldata.csv";

    private static JdbcTemplate jdbc;
    private static EconomicIndicatorJdbcRepository repository;
    private static int second;

    @BeforeAll
    static void prepare() throws Exception {
        String url = System.getenv("ENVIRONMENTAL_TEST_DB_URL");
        if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/[A-Za-z0-9_]+_test")) {
            throw new IllegalStateException("Only a dedicated local database whose name ends with _test is allowed");
        }
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(System.getenv("DB_USER"));
        dataSource.setPassword(System.getenv("DB_PASSWORD"));
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        dataSource.setCurrentSchema(SCHEMA);
        jdbc = new JdbcTemplate(dataSource);
        // The part of counties that V50 and the repository use (fips_code comes from V42)
        jdbc.execute("CREATE TABLE counties (id bigint PRIMARY KEY, name varchar(100) NOT NULL, fips_code varchar(5))");
        jdbc.execute("INSERT INTO counties VALUES (1, 'Miami-Dade', '12086'), (2, 'Broward', '12011'), "
                + "(3, 'Fulton', '13121'), (4, 'No code', NULL)");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V50__add_economic_indicators.sql")));
        repository = new EconomicIndicatorJdbcRepository(new NamedParameterJdbcTemplate(dataSource));
    }

    @AfterAll
    static void cleanup() {
        // SCHEMA is generated here, never supplied by a caller; the database URL is checked above.
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @BeforeEach
    void empty() {
        jdbc.execute("TRUNCATE " + TABLE + ", economic_indicator_loads RESTART IDENTITY");
    }

    /** A new import time each call, as each import has its own. */
    private static OffsetDateTime nextImport() {
        second++;
        return OffsetDateTime.of(2026, 11, 25, 9, 0, second, 123456000, ZoneOffset.UTC);
    }

    private static EconomicObservation population(int year, String value) {
        LocalDate july1 = LocalDate.of(year, 7, 1);
        return new EconomicObservation("POPULATION", "12086", "Miami-Dade County", july1, july1,
                new BigDecimal(value), EconomicObservation.ESTIMATE, "POPESTIMATE" + year);
    }

    private static RunCounts load(List<EconomicObservation> values, String vintage, String url, OffsetDateTime now) {
        repository.upsert(values, vintage, url, now);
        return repository.countRun(CENSUS, now);
    }

    private static Map<String, Object> row(int year) {
        return jdbc.queryForMap("SELECT value, previous_value, revision_count, source_vintage, source_url, "
                        + "revised_at FROM " + TABLE + " WHERE indicator_code = 'POPULATION' AND period_start = ?",
                LocalDate.of(year, 7, 1));
    }

    @Test
    @DisplayName("V50 creates the 7 indicator definitions with their unit, frequency, geography and source")
    void whenApplied_thenSevenDefinitions() {
        List<EconomicIndicatorDTO> indicators = repository.findIndicators();

        assertEquals(7, indicators.size());
        assertEquals("EMPLOYMENT", indicators.get(0).code());
        EconomicIndicatorDTO rate = indicators.stream().filter(i -> i.code().equals("UNEMPLOYMENT_RATE")).findFirst()
                .orElseThrow();
        assertEquals("PERCENT", rate.unit());
        assertEquals("MONTHLY", rate.frequency());
        assertEquals("COUNTY", rate.geographyLevel());
        assertEquals("BLS_LAUS", rate.source());
        assertEquals(0, rate.coverage().observations());
        assertNull(rate.coverage().firstPeriod());
    }

    @Test
    @DisplayName("When a newer vintage changes a value, then the same row is revised: old value kept, count + 1")
    void whenNewerVintage_thenRevisedWithoutDuplicate() {
        RunCounts first = load(List.of(population(2024, "2838461"), population(2023, "2774250")), "2024", URL_2024,
                nextImport());
        assertEquals(new RunCounts(2, 0, 2), first);

        OffsetDateTime revision = nextImport();
        RunCounts second = load(List.of(population(2024, "2812144"), population(2023, "2774250"),
                population(2025, "2802029")), "2025", URL_2025, revision);

        assertEquals(new RunCounts(1, 1, 3), second);
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM " + TABLE, Integer.class));
        Map<String, Object> revised = row(2024);
        assertEquals(0, new BigDecimal("2812144").compareTo((BigDecimal) revised.get("value")));
        assertEquals(0, new BigDecimal("2838461").compareTo((BigDecimal) revised.get("previous_value")));
        assertEquals(1, revised.get("revision_count"));
        assertEquals("2025", revised.get("source_vintage"));
        assertEquals(URL_2025, revised.get("source_url"));
        Map<String, Object> unchanged = row(2023);
        assertEquals(0, unchanged.get("revision_count"));
        assertNull(unchanged.get("previous_value"));
        assertNull(unchanged.get("revised_at"));
    }

    @Test
    @DisplayName("When the same vintage is imported again, then nothing is revised and nothing is added")
    void whenSameVintageAgain_thenUnchanged() {
        load(List.of(population(2024, "2812144")), "2025", URL_2025, nextImport());

        RunCounts again = load(List.of(population(2024, "2812144")), "2025", URL_2025, nextImport());

        assertEquals(new RunCounts(0, 0, 1), again);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + TABLE, Integer.class));
        assertEquals(0, row(2024).get("revision_count"));
    }

    @Test
    @DisplayName("When an older vintage is imported after a newer one, then the newer value is kept")
    void whenOlderVintage_thenNotWritten() {
        load(List.of(population(2024, "2812144")), "2025", URL_2025, nextImport());

        RunCounts older = load(List.of(population(2024, "2838461")), "2024", URL_2024, nextImport());

        assertEquals(new RunCounts(0, 0, 0), older);
        Map<String, Object> kept = row(2024);
        assertEquals(0, new BigDecimal("2812144").compareTo((BigDecimal) kept.get("value")));
        assertEquals("2025", kept.get("source_vintage"));
    }

    @Test
    @DisplayName("When reading a county, then the values are oldest first, filtered on the end of the period")
    void whenRead_thenOrderedAndFiltered() {
        LocalDate start = LocalDate.of(2023, 7, 1);
        EconomicObservation migration2024 = new EconomicObservation("NET_MIGRATION", "12086", "Miami-Dade County",
                start, LocalDate.of(2024, 6, 30), new BigDecimal("10015"), EconomicObservation.ESTIMATE, "NETMIG2024");
        load(List.of(population(2025, "2802029"), population(2024, "2812144"), migration2024), "2025", URL_2025,
                nextImport());

        List<EconomicIndicatorObservationDTO> all = repository.findObservations("POPULATION", "12086", false, null,
                null);
        List<EconomicIndicatorObservationDTO> only2024 = repository.findObservations("NET_MIGRATION", "12086", false,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        assertEquals(List.of("2024", "2025"), all.stream().map(EconomicIndicatorObservationDTO::period).toList());
        assertEquals(1, only2024.size());
        assertEquals("2024", only2024.get(0).period());
        assertTrue(repository.findObservations("POPULATION", "12011", false, null, null).isEmpty());
    }

    @Test
    @DisplayName("When reading a monthly indicator, then the period is written year-month")
    void whenMonthly_thenYearMonthLabel() {
        EconomicObservation september = new EconomicObservation("UNEMPLOYMENT_RATE", "12086", "Miami-Dade",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), new BigDecimal("1.9"),
                EconomicObservation.ESTIMATE, "LAUCN120860000000003");
        repository.upsert(List.of(september), "2026-11-25", "https://api.bls.gov/", nextImport());

        EconomicIndicatorObservationDTO read = repository.findObservations("UNEMPLOYMENT_RATE", "12086", true, null,
                null).get(0);

        assertEquals("2026-09", read.period());
        assertEquals(0, new BigDecimal("1.9").compareTo(read.value()));
        assertEquals("ESTIMATE", read.observationKind());
    }

    @Test
    @DisplayName("When values are stored, then the coverage counts counties, periods, estimates and freshness")
    void whenStored_thenCoverage() {
        OffsetDateTime now = nextImport();
        load(List.of(population(2024, "2812144"), population(2025, "2802029")), "2025", URL_2025, now);

        EconomicIndicatorDTO population = repository.findIndicator("POPULATION").orElseThrow();

        assertEquals(1, population.coverage().counties());
        assertEquals(2, population.coverage().observations());
        assertEquals(2, population.coverage().estimateObservations());
        assertEquals(LocalDate.of(2024, 7, 1), population.coverage().firstPeriod());
        assertEquals(LocalDate.of(2025, 7, 1), population.coverage().lastPeriod());
        assertEquals(now.toInstant(), population.coverage().lastVerifiedAt());
        assertTrue(repository.findIndicator("GDP").isEmpty());
    }

    @Test
    @DisplayName("When listing the counties of a state, then only its counties with a FIPS code, by code")
    void whenCounties_thenStateOnly() {
        assertEquals(Map.of("12011", "Broward", "12086", "Miami-Dade"), repository.findCounties("12"));
        assertEquals(List.of("12011", "12086"), List.copyOf(repository.findCounties("12").keySet()));
    }

    @Test
    @DisplayName("When an import is recorded, then its counts and its user are in the load history")
    void whenRecordLoad_thenHistory() {
        Instant importedAt = Instant.parse("2026-11-25T09:00:00.123456Z");
        repository.recordLoad(new EconomicIndicatorImportResultDTO("CENSUS_PEP", "12", 67, URL_2025, "2025",
                        LocalDate.of(2020, 7, 1), LocalDate.of(2025, 7, 1), 1407, 268, 1070, 69, 0, 0, List.of(), importedAt),
                "admin");

        Map<String, Object> load = jdbc.queryForMap("SELECT * FROM economic_indicator_loads");
        assertEquals("CENSUS_PEP", load.get("source"));
        assertEquals(1070, load.get("revised"));
        assertEquals("admin", load.get("loaded_by"));
    }

    @Test
    @DisplayName("V50 refuses an unknown indicator, a bad county code, an unknown kind and the same period twice")
    void whenRowInvalid_thenRefused() {
        String insert = "INSERT INTO " + TABLE + " (indicator_code, geography_level, geography_code, period_start, "
                + "period_end, value, observation_kind, source_vintage, source_url, ingested_at, last_verified_at) "
                + "VALUES (?, 'COUNTY', ?, DATE '2024-07-01', DATE '2024-07-01', 1, ?, '2025', 'u', now(), now())";

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, "GDP", "12086", "ESTIMATE"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, "POPULATION", "1208", "ESTIMATE"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, "POPULATION", "12086", "GUESS"));
        jdbc.update(insert, "POPULATION", "12086", "ESTIMATE");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(insert, "POPULATION", "12086", "ACTUAL"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + TABLE, Integer.class));
    }
}
