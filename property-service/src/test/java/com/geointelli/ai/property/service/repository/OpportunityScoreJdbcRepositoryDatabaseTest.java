package com.geointelli.ai.property.service.repository;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.dto.OpportunitySignals;
import com.geointelli.ai.property.service.repository.OpportunityScoreJdbcRepository.ScoredProperty;
import com.geointelli.ai.property.service.service.OpportunityScoringService;
import com.geointelli.ai.property.service.service.impl.OpportunityScoringServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in checks of the opportunity score SQL and the V40 table on a real PostgreSQL database. Runs in a schema of
 * its own on a dedicated local test database only, and drops it afterwards; never the application's database.
 */
@EnabledIfEnvironmentVariable(named = "OPPORTUNITY_TEST_DB_URL", matches = ".+")
class OpportunityScoreJdbcRepositoryDatabaseTest {

    private static final String SCHEMA = "opportunity_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String VERSION = OpportunityScoringService.SCORE_VERSION;
    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    private static JdbcTemplate jdbc;
    private static OpportunityScoreJdbcRepository repository;
    private static OpportunityScoringService scoring;

    @BeforeAll
    static void prepare() throws Exception {
        String url = System.getenv("OPPORTUNITY_TEST_DB_URL");
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
        jdbc.execute("CREATE TABLE states (id bigint PRIMARY KEY)");
        jdbc.execute("CREATE TABLE counties (id bigint PRIMARY KEY, state_id bigint REFERENCES states(id))");
        jdbc.execute("CREATE TABLE properties (id bigint PRIMARY KEY, county_id bigint REFERENCES counties(id))");
        jdbc.execute("""
                CREATE TABLE property_value_predictions (id bigserial PRIMARY KEY, property_id bigint UNIQUE NOT NULL,
                    valuation_status varchar(12), predicted_price numeric(38,2), forecast_1y numeric(38,2),
                    forecast_3y numeric(38,2), market_outlook_1y_pct double precision, confidence varchar(10),
                    confidence_score integer, model_version varchar(32), data_through date, run_id varchar(32))""");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V40__add_property_opportunity_scores.sql")));
        repository = new OpportunityScoreJdbcRepository(new NamedParameterJdbcTemplate(dataSource),
                JsonMapper.builder().addModule(new JavaTimeModule()).build());
        scoring = new OpportunityScoringServiceImpl(repository);
    }

    @AfterAll
    static void cleanup() {
        // SCHEMA is generated here, never supplied by a caller; the database URL is checked above.
        if (jdbc != null) {
            jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }

    @BeforeEach
    void data() {
        jdbc.execute("TRUNCATE property_opportunity_scores, property_value_predictions, properties, counties, states");
        jdbc.update("INSERT INTO states VALUES (10)");
        jdbc.update("INSERT INTO counties VALUES (1, 10)");
        jdbc.update("INSERT INTO properties VALUES (1, 1), (2, 1), (3, 1), (4, NULL)");
        valuation(1, "supported", "300000", "312000", "run-a");
        valuation(2, "unsupported", null, null, "run-a");
        valuation(3, "supported", "500000", "490000", "run-a");
        // property 4 has no valuation
    }

    private static void valuation(long propertyId, String status, String value, String forecast1y, String run) {
        jdbc.update("""
                INSERT INTO property_value_predictions (property_id, valuation_status, predicted_price, forecast_1y,
                    forecast_3y, market_outlook_1y_pct, confidence, confidence_score, model_version, data_through, run_id)
                VALUES (?, ?, ?, ?, NULL, -0.17, 'high', 85, 'v17+v18', DATE '2026-07-26', ?)""",
                propertyId, status, value == null ? null : new BigDecimal(value),
                forecast1y == null ? null : new BigDecimal(forecast1y), run);
    }

    private static List<ScoredProperty> score(List<OpportunitySignals> signals) {
        return signals.stream().map(s -> new ScoredProperty(s, scoring.score(s, NOW))).toList();
    }

    @Test
    @DisplayName("Reads a property's location and valuation, a property without valuation, and nothing for an unknown ID")
    void findSignals_readsTheStoredEvidence() {
        OpportunitySignals valued = repository.findSignals(1).orElseThrow();
        assertTrue(valued.hasValuation());
        assertEquals(1L, valued.countyId());
        assertEquals(10L, valued.stateId());
        assertEquals(0, new BigDecimal("300000").compareTo(valued.estimatedValue()));
        assertEquals(-0.17, valued.marketOutlook1yPct());
        assertEquals(85, valued.confidenceScore());
        assertEquals(LocalDate.of(2026, 7, 26), valued.dataThrough());
        assertNull(valued.forecast3y());

        OpportunitySignals withoutValuation = repository.findSignals(4).orElseThrow();
        assertFalse(withoutValuation.hasValuation());
        assertNull(withoutValuation.countyId());

        assertTrue(repository.findSignals(999).isEmpty());
    }

    @Test
    @DisplayName("Stores scores, then returns only missing, outdated-version or new-valuation-run properties")
    void incrementalCandidates_followTheStoredScores() {
        List<OpportunitySignals> first = repository.findCandidates(0, 10, false, VERSION);
        assertEquals(List.of(1L, 2L, 3L), first.stream().map(OpportunitySignals::propertyId).toList());

        repository.upsert(score(first));
        assertTrue(repository.findCandidates(0, 10, false, VERSION).isEmpty());
        assertEquals(3, repository.findCandidates(0, 10, true, VERSION).size(), "FULL mode returns everything");

        jdbc.update("UPDATE property_value_predictions SET run_id = 'run-b' WHERE property_id = 3");
        jdbc.update("UPDATE property_opportunity_scores SET score_version = 'opportunity-v0' WHERE property_id = 1");
        assertEquals(List.of(1L, 3L), repository.findCandidates(0, 10, false, VERSION).stream()
                .map(OpportunitySignals::propertyId).toList());
        assertEquals(List.of(3L), repository.findCandidates(1, 10, false, VERSION).stream()
                .map(OpportunitySignals::propertyId).toList(), "keyset continues after the given ID");
        assertEquals(1, repository.findCandidates(0, 1, false, VERSION).size(), "limit is applied");
    }

    @Test
    @DisplayName("Stored rows keep the score, status, factors, location and valuation basis; an upsert replaces them")
    void upsert_writesAndReplacesRows() {
        repository.upsert(score(repository.findCandidates(0, 10, true, VERSION)));

        Map<String, Object> scored = jdbc.queryForMap("SELECT * FROM property_opportunity_scores WHERE property_id = 1");
        assertEquals("SCORED", scored.get("status"));
        assertEquals(VERSION, scored.get("score_version"));
        assertEquals(1L, scored.get("county_id"));
        assertEquals(10L, scored.get("state_id"));
        assertEquals("run-a", scored.get("valuation_run_id"));
        assertEquals(0, new BigDecimal("4.0000").compareTo((BigDecimal) scored.get("forecast_growth_1y_pct")));
        assertEquals(7, jdbc.queryForObject(
                "SELECT jsonb_array_length(factors) FROM property_opportunity_scores WHERE property_id = 1", Integer.class));
        assertEquals("UNAVAILABLE", jdbc.queryForObject(
                "SELECT factors -> 4 ->> 'status' FROM property_opportunity_scores WHERE property_id = 1", String.class));

        Map<String, Object> unavailable = jdbc.queryForMap("SELECT * FROM property_opportunity_scores WHERE property_id = 2");
        assertEquals("UNAVAILABLE", unavailable.get("status"));
        assertNull(unavailable.get("score"));
        assertNull(unavailable.get("forecast_growth_1y_pct"), "no forecast is stored as no growth, not 0");
        assertEquals("VALUATION_UNSUPPORTED", jdbc.queryForObject(
                "SELECT reasons -> 0 ->> 'code' FROM property_opportunity_scores WHERE property_id = 2", String.class));

        jdbc.update("UPDATE property_value_predictions SET forecast_1y = 330000, run_id = 'run-b' WHERE property_id = 1");
        repository.upsert(score(repository.findCandidates(0, 10, false, VERSION)));
        Map<String, Object> replaced = jdbc.queryForMap("SELECT * FROM property_opportunity_scores WHERE property_id = 1");
        assertEquals("run-b", replaced.get("valuation_run_id"));
        assertEquals(0, new BigDecimal("10.0000").compareTo((BigDecimal) replaced.get("forecast_growth_1y_pct")));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM property_opportunity_scores", Integer.class));
    }

    @Test
    @DisplayName("Removes stored scores whose property no longer has a valuation")
    void deleteWithoutValuation_removesOrphans() {
        repository.upsert(score(repository.findCandidates(0, 10, true, VERSION)));
        jdbc.update("DELETE FROM property_value_predictions WHERE property_id = 3");

        assertEquals(1, repository.deleteWithoutValuation());
        assertEquals(List.of(1L, 2L), jdbc.queryForList(
                "SELECT property_id FROM property_opportunity_scores ORDER BY property_id", Long.class));
    }

    @Test
    @DisplayName("The table rejects a SCORED row without a score")
    void constraint_rejectsScoredRowWithoutScore() {
        assertThrows(Exception.class, () -> jdbc.update("""
                INSERT INTO property_opportunity_scores (property_id, score_version, status, score, coverage_percent,
                    factors, reasons, scored_at) VALUES (1, 'v', 'SCORED', NULL, 50, '[]', '[]', now())"""));
    }
}
