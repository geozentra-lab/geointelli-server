package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.geointelli.ai.property.service.entity.AuditableEntity;
import com.geointelli.ai.property.service.entity.MarketEvidenceObservation;

import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;

/**
 * Opt-in checks of migration V48, market evidence (SCRUM-131), on a real PostgreSQL + PostGIS database, with the
 * same rules as SchoolReferenceDataDatabaseTest: a schema of its own on a dedicated local test database whose name
 * ends with _test, dropped afterwards; never the application's database. V42 runs first, because COUNTY rows are
 * linked through counties.fips_code.
 */
@EnabledIfEnvironmentVariable(named = "ENVIRONMENTAL_TEST_DB_URL", matches = ".+")
class MarketEvidenceDatabaseTest {

    private static final String SCHEMA = "market_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String TABLE = "market_evidence_observations";
    /** A SALE row of ZIP 33139 for October 2026; the currency and the asking price are the parameters. */
    private static final String INSERT_SALE = "INSERT INTO " + TABLE + " (evidence_type, source, geography_level, "
        + "geography_code, period_start, period_end, currency, sale_count, median_asking_price, imported_at, "
        + "created_at, updated_at) VALUES ('SALE', 'P', 'ZIP', '33139', DATE '2026-10-01', DATE '2026-10-31', ?, 4, ?, "
        + "now(), now(), now())";

    private static JdbcTemplate jdbc;

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
        // PostGIS lives in public.
        dataSource.setCurrentSchema(SCHEMA + ",public");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE states (id bigint PRIMARY KEY, code varchar(2) NOT NULL, name varchar(100) NOT NULL)");
        jdbc.execute("CREATE TABLE counties (id bigint PRIMARY KEY, state_id bigint NOT NULL REFERENCES states(id), "
            + "name varchar(100) NOT NULL)");
        jdbc.execute("INSERT INTO states VALUES (1, 'FL', 'Florida')");
        jdbc.execute("INSERT INTO counties VALUES (1, 1, 'Miami-Dade'), (2, 1, 'Alachua')");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V42__add_environmental_reference_data.sql")));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V48__add_market_evidence_observations.sql")));
    }

    @AfterAll
    static void cleanup() {
        // SCHEMA is generated here, never supplied by a caller; the database URL is checked above.
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @BeforeEach
    void empty() {
        jdbc.execute("TRUNCATE " + TABLE + " RESTART IDENTITY");
    }

    private static Map<String, Object> column(String name) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT data_type, character_maximum_length, numeric_precision, "
            + "numeric_scale, is_nullable FROM information_schema.columns "
            + "WHERE table_schema = ? AND table_name = ? AND column_name = ?", SCHEMA, TABLE, name);
        assertEquals(1, rows.size(), "column " + name + " of " + TABLE);
        return rows.get(0);
    }

    @Test
    @DisplayName("V48 matches the entity: every mapped column exists with the entity's type, size and nullability")
    void whenApplied_thenMatchesEntity() {
        List<Field> fields = new ArrayList<>(List.of(MarketEvidenceObservation.class.getDeclaredFields()));
        fields.addAll(List.of(AuditableEntity.class.getDeclaredFields()));
        int checked = 0;
        for (Field field : fields) {
            JoinColumn join = field.getAnnotation(JoinColumn.class);
            if (join != null) {
                assertEquals("bigint", column(join.name()).get("data_type"), join.name());
                checked++;
            }
            Column mapped = field.getAnnotation(Column.class);
            if (mapped == null) continue;
            String name = mapped.name().isEmpty() ? field.getName() : mapped.name();
            Map<String, Object> info = column(name);
            if (field.getType() == String.class || field.getType().isEnum()) {
                // CHAR would be bpchar, which Hibernate's schema validation refuses for a String (SCRUM-131 review)
                assertEquals("character varying", info.get("data_type"), name);
                assertEquals(mapped.length(), ((Number) info.get("character_maximum_length")).intValue(), name);
            } else if (field.getType() == BigDecimal.class) {
                assertEquals("numeric", info.get("data_type"), name);
                assertEquals(mapped.precision(), ((Number) info.get("numeric_precision")).intValue(), name);
                assertEquals(mapped.scale(), ((Number) info.get("numeric_scale")).intValue(), name);
            }
            if (!mapped.nullable()) assertEquals("NO", info.get("is_nullable"), name + " NOT NULL");
            checked++;
        }
        // 24 @Column fields, the state and county join columns, created_at and updated_at
        assertTrue(checked >= 28, "checked " + checked + " columns");
    }

    @Test
    @DisplayName("V48 refuses a lower-case currency, a SALE row with an asking figure, and the same observation twice")
    void whenRowInvalid_thenRefused() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(INSERT_SALE, "usd", null));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(INSERT_SALE, "USD", new BigDecimal("1")));
        jdbc.update(INSERT_SALE, "USD", null);
        assertThrows(DuplicateKeyException.class, () -> jdbc.update(INSERT_SALE, "EUR", null));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + TABLE, Integer.class));
        assertEquals("USD", jdbc.queryForObject("SELECT currency FROM " + TABLE, String.class));
    }

    @Test
    @DisplayName("After V42, the FIPS lookup of CountyRepository finds the county of a COUNTY row")
    void whenFipsCode_thenCountyFound() {
        // The query of CountyRepository.findIdByFipsCode
        assertEquals(1L, jdbc.queryForObject("SELECT id FROM counties WHERE fips_code = ?", Long.class, "12086"));
        assertTrue(jdbc.queryForList("SELECT id FROM counties WHERE fips_code = ?", Long.class, "13121").isEmpty());
    }
}
