package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.ReferenceDataImportService.InvalidReferenceRowException;
import com.geointelli.ai.property.service.service.ReferenceDataImportService.LoadRequest;
import com.geointelli.ai.property.service.service.ReferenceDataImportService.LoadResult;

/**
 * Opt-in checks of migrations V42/V47 and the COPY loader on a real PostgreSQL + PostGIS database (H2 has no PostGIS).
 * Runs in a schema of its own on a dedicated local test database only, and drops it afterwards; never the
 * application's database. Fixture: src/test/resources/reference-data.
 */
@EnabledIfEnvironmentVariable(named = "ENVIRONMENTAL_TEST_DB_URL", matches = ".+")
class ReferenceDataImportDatabaseTest {

    private static final String SCHEMA = "reference_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final Path FIXTURES = Path.of("src/test/resources/reference-data");
    private static final LocalDate DOWNLOADED = LocalDate.of(2026, 11, 14);

    private static PGSimpleDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transaction;
    private static ReferenceDataImportServiceImpl importer;

    @TempDir
    Path temp;

    @BeforeAll
    static void prepare() throws Exception {
        String url = System.getenv("ENVIRONMENTAL_TEST_DB_URL");
        if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/[A-Za-z0-9_]+_test")) {
            throw new IllegalStateException("Only a dedicated local database whose name ends with _test is allowed");
        }
        dataSource = new PGSimpleDataSource();
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
        jdbc.execute("INSERT INTO states VALUES (1, 'FL', 'Florida'), (2, 'GA', 'Georgia')");
        jdbc.execute("INSERT INTO counties VALUES (1, 1, 'Miami-Dade'), (2, 1, 'Saint Johns'), (3, 1, 'De Soto'), "
            + "(4, 1, 'St. Lucie'), (5, 2, 'Fulton')");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V42__add_environmental_reference_data.sql")));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V47__widen_flood_zones_study_typ.sql")));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        importer = new ReferenceDataImportServiceImpl(dataSource, new ObjectMapper());
    }

    @AfterAll
    static void cleanup() {
        // SCHEMA is generated here, never supplied by a caller; the database URL is checked above.
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    private static LoadResult load(IngestionDataset dataset, Path file, String scope) {
        return transaction.execute(status -> {
            try {
                return importer.load(new LoadRequest(dataset, file, file.getFileName().toString(), scope, DOWNLOADED, null));
            } catch (java.io.IOException unreadable) {
                throw new java.io.UncheckedIOException(unreadable);
            }
        });
    }

    @Test
    @DisplayName("V42 sets the FIPS code of Florida counties by name, whatever the spelling of Saint/St., De Soto or Miami-Dade")
    void migrationMatchesCountyNames() {
        Map<String, String> codes = new java.util.HashMap<>();
        jdbc.query("SELECT name, fips_code FROM counties", row -> {
            codes.put(row.getString(1), row.getString(2));
        });
        assertEquals("12086", codes.get("Miami-Dade"));
        assertEquals("12109", codes.get("Saint Johns"));
        assertEquals("12027", codes.get("De Soto"));
        assertEquals("12111", codes.get("St. Lucie"));
        assertNull(codes.get("Fulton"), "only Florida counties are matched");
    }

    @Test
    @DisplayName("V42 refuses to finish when a Florida county name has no FIPS code")
    void migrationFailsOnUnknownFloridaCounty() throws Exception {
        String other = SCHEMA + "_unmatched";
        jdbc.execute("CREATE SCHEMA " + other);
        try {
            var failing = new PGSimpleDataSource();
            failing.setUrl(dataSource.getUrl());
            failing.setUser(System.getenv("DB_USER"));
            failing.setPassword(System.getenv("DB_PASSWORD"));
            failing.setCurrentSchema(other + ",public");
            JdbcTemplate otherJdbc = new JdbcTemplate(failing);
            otherJdbc.execute("CREATE TABLE states (id bigint PRIMARY KEY, code varchar(2) NOT NULL, name varchar(100) NOT NULL)");
            otherJdbc.execute("CREATE TABLE counties (id bigint PRIMARY KEY, state_id bigint NOT NULL, name varchar(100) NOT NULL)");
            otherJdbc.execute("INSERT INTO states VALUES (1, 'FL', 'Florida')");
            otherJdbc.execute("INSERT INTO counties VALUES (1, 1, 'Atlantis')");
            String migration = Files.readString(Path.of("src/main/resources/db/migration/V42__add_environmental_reference_data.sql"));
            var error = assertThrows(org.springframework.dao.DataAccessException.class, () -> otherJdbc.execute(migration));
            assertTrue(error.getMostSpecificCause().getMessage().contains("Atlantis"));
        } finally {
            jdbc.execute("DROP SCHEMA " + other + " CASCADE");
        }
    }

    @Test
    @DisplayName("Flood zones keep every source row: -9999 is NULL, repeated FLD_AR_ID and multipolygons are kept, "
        + "an invalid polygon is repaired, a polygon without area keeps a NULL geometry, and every geometry is valid")
    void floodZonesFixture() {
        LoadResult result = load(IngestionDataset.FLOOD_ZONES, FIXTURES.resolve("12086_flood_fixture.csv"), "12086");

        assertEquals(7, result.rowsRead());
        assertEquals(7, result.rowsLoaded());
        assertEquals(2, result.rowsWithoutGeometry(), "the zero-area polygon and the empty geometry");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM flood_zones WHERE fips5 = '12086' AND geom IS NOT NULL AND NOT ST_IsValid(geom)", Integer.class));
        Map<String, Object> first = jdbc.queryForMap("SELECT * FROM flood_zones WHERE fips5 = '12086' AND source_row = 1");
        assertEquals(0, new BigDecimal("7").compareTo((BigDecimal) first.get("static_bfe")));
        assertNull(first.get("depth"), "-9999 means none");
        assertNull(first.get("velocity"));
        assertEquals(Boolean.TRUE, first.get("sfha"));
        Map<String, Object> second = jdbc.queryForMap("SELECT * FROM flood_zones WHERE fips5 = '12086' AND source_row = 2");
        assertNull(second.get("static_bfe"));
        assertNull(second.get("velocity"), "-99999 means none too");
        assertNull(second.get("v_datum"), "empty is NULL, not an empty string");
        assertEquals(Boolean.FALSE, second.get("sfha"));
        assertEquals("0.2 PCT ANNUAL CHANCE FLOOD HAZARD", second.get("zone_subty"));
        assertEquals(2, jdbc.queryForObject("SELECT count(DISTINCT source_row) FROM flood_zones WHERE fips5 = '12086' AND fld_ar_id = '12086C_1'", Integer.class),
            "both rows with the repeated FLD_AR_ID are kept");
        assertTrue(jdbc.queryForObject("SELECT ST_Area(ST_Union(geom)) > 0 FROM flood_zones WHERE fips5 = '12086' AND source_row = 3", Boolean.class),
            "the self-intersecting polygon is repaired, not dropped");
        assertEquals(2, jdbc.queryForObject("SELECT ST_NumGeometries(ST_Union(geom)) FROM flood_zones WHERE fips5 = '12086' AND source_row = 5", Integer.class));
        assertNull(jdbc.queryForObject("SELECT geom FROM flood_zones WHERE fips5 = '12086' AND source_row = 6", Object.class));
        assertEquals("12086_flood_fixture.csv", jdbc.queryForObject("SELECT DISTINCT source_file FROM flood_zones WHERE fips5 = '12086'", String.class));
    }

    @Test
    @DisplayName("A polygon with more vertices than the limit is cut into valid parts that cover the same area")
    void largePolygonIsSubdivided() throws Exception {
        StringBuilder ring = new StringBuilder("POLYGON((");
        int points = 2000;
        for (int i = 0; i <= points; i++) {
            double angle = 2 * Math.PI * (i % points) / points;
            if (i > 0) ring.append(',');
            ring.append(String.format(java.util.Locale.ROOT, "%.6f %.6f", -80.0 + 0.05 * Math.cos(angle), 25.5 + 0.05 * Math.sin(angle)));
        }
        ring.append("))");
        Path file = temp.resolve("12087_large.csv");
        Files.writeString(file, "fips5,DFIRM_ID,FLD_AR_ID,FLD_ZONE,ZONE_SUBTY,SFHA_TF,STATIC_BFE,DEPTH,V_DATUM,LEN_UNIT,"
            + "VELOCITY,VEL_UNIT,DUAL_ZONE,STUDY_TYP,VERSION_ID,SOURCE_CIT,geom_wkt\n"
            + "12087,12087C,12087C_1,AE,,T,5,-9999,,,-9999,,,NP,1.1.1.0,x,\"" + ring + "\"\n", StandardCharsets.UTF_8);

        LoadResult result = load(IngestionDataset.FLOOD_ZONES, file, "12087");

        assertEquals(1, result.rowsLoaded());
        assertTrue(result.rowsWritten() > 1, "cut into parts");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM flood_zones WHERE fips5 = '12087' AND ST_NPoints(geom) > 256", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM flood_zones WHERE fips5 = '12087' AND NOT ST_IsValid(geom)", Integer.class));
        assertTrue(jdbc.queryForObject("""
            SELECT abs(ST_Area(ST_Union(geom)) - ST_Area(ST_GeomFromText(?, 4326))) < 1e-9
            FROM flood_zones WHERE fips5 = '12087'""", Boolean.class, ring.toString()));
    }

    @Test
    @DisplayName("The longest value of each FEMA text column in the 67 Florida files is stored whole (STUDY_TYP: 28)")
    void longestFemaValuesFit() throws Exception {
        String square = "\"POLYGON((-82.30 29.60,-82.29 29.60,-82.29 29.61,-82.30 29.61,-82.30 29.60))\"";
        // Every STUDY_TYP longer than V42's 20 characters that the FEMA files use.
        List<String> studyTypes = List.of("SFHAs WITH MEDIUM FLOOD RISK", "SFHAs WITH HIGH FLOOD RISK",
            "SFHA with BFE and floodway", "SFHAs WITH LOW FLOOD RISK", "SFHA with unpublished BFE",
            "SFHA with BFE no floodway");
        StringBuilder csv = new StringBuilder("fips5,DFIRM_ID,FLD_AR_ID,FLD_ZONE,ZONE_SUBTY,SFHA_TF,STATIC_BFE,DEPTH,"
            + "V_DATUM,LEN_UNIT,VELOCITY,VEL_UNIT,DUAL_ZONE,STUDY_TYP,VERSION_ID,SOURCE_CIT,geom_wkt\n");
        for (int i = 0; i < studyTypes.size(); i++) {
            csv.append("12001,12001C,12001C_10833").append(i).append(",OPEN WATER,")
                .append("0.2 PCT ANNUAL CHANCE FLOOD HAZARD IN COMBINED RIVERINE AND COASTAL ZONE,F,-9999,-9999,NAVD88,")
                .append("Centimeters,-9999,Feet / Second,F,").append(studyTypes.get(i)).append(",1.1.1.0,12005C_STUDY12,")
                .append(square).append('\n');
        }
        Path file = temp.resolve("12001_alachua.csv");
        Files.writeString(file, csv, StandardCharsets.UTF_8);

        LoadResult result = load(IngestionDataset.FLOOD_ZONES, file, "12001");

        assertEquals(studyTypes.size(), result.rowsLoaded());
        assertEquals(studyTypes, jdbc.queryForList(
            "SELECT study_typ FROM flood_zones WHERE fips5 = '12001' ORDER BY source_row", String.class));
        Map<String, Object> first = jdbc.queryForMap("SELECT * FROM flood_zones WHERE fips5 = '12001' AND source_row = 1");
        assertEquals("0.2 PCT ANNUAL CHANCE FLOOD HAZARD IN COMBINED RIVERINE AND COASTAL ZONE", first.get("zone_subty"));
        assertEquals("Centimeters", first.get("len_unit"));
        assertEquals("Feet / Second", first.get("vel_unit"));
        assertEquals("12005C_STUDY12", first.get("source_cit"));
    }

    @Test
    @DisplayName("Re-importing a county replaces its rows without duplicates; a bad file leaves the previous rows as they were")
    void reimportReplacesCounty() throws Exception {
        Path fixture = FIXTURES.resolve("12086_flood_fixture.csv");
        load(IngestionDataset.FLOOD_ZONES, fixture, "12086");
        int before = jdbc.queryForObject("SELECT count(*) FROM flood_zones WHERE fips5 = '12086'", Integer.class);
        load(IngestionDataset.FLOOD_ZONES, fixture, "12086");
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM flood_zones WHERE fips5 = '12086'", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT count(DISTINCT source_row) FROM flood_zones WHERE fips5 = '12086'", Integer.class));

        Path wrongCounty = temp.resolve("12086_wrong.csv");
        List<String> lines = Files.readAllLines(fixture);
        Files.write(wrongCounty, List.of(lines.get(0), lines.get(1), lines.get(2).replaceFirst("^12086", "12087")));
        var refused = assertThrows(InvalidReferenceRowException.class,
            () -> load(IngestionDataset.FLOOD_ZONES, wrongCounty, "12086"));
        assertEquals(2, refused.row());
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM flood_zones WHERE fips5 = '12086'", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM reference_dataset_loads WHERE file = '12086_flood_fixture.csv'", Integer.class) >= 2);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reference_dataset_loads WHERE file = '12086_wrong.csv'", Integer.class));
    }

    @Test
    @DisplayName("NRI: 'Insufficient Data', 'No Rating' (score 0) and 'Not Applicable' are NULL, never low; raw keeps the source text")
    void nriFixture() {
        LoadResult result = load(IngestionDataset.NRI_TRACTS, FIXTURES.resolve("12_nri_fixture.csv"), "12");

        assertEquals(3, result.rowsLoaded());
        Map<String, Object> high = jdbc.queryForMap("SELECT * FROM nri_tract_risk WHERE tract_fips = '12086000100'");
        assertEquals("Very High", high.get("risk_rating"));
        assertEquals(0, new BigDecimal("55.123456").compareTo((BigDecimal) high.get("ltng_score")));
        assertEquals("December 2025", high.get("nri_version"));
        Map<String, Object> unknown = jdbc.queryForMap("SELECT * FROM nri_tract_risk WHERE tract_fips = '12086000200'");
        assertNull(unknown.get("hrcn_rating"), "Insufficient Data");
        assertNull(unknown.get("hrcn_score"));
        assertNull(unknown.get("ifld_rating"), "No Rating");
        assertNull(unknown.get("ifld_score"), "the 0 next to No Rating is not a low score");
        assertNull(unknown.get("cfld_rating"), "Not Applicable");
        assertNull(unknown.get("wfir_score"));
        assertEquals("Relatively Low", unknown.get("trnd_rating"));
        assertEquals("Insufficient Data", jdbc.queryForObject(
            "SELECT raw->>'HRCN_RISKR' FROM nri_tract_risk WHERE tract_fips = '12086000200'", String.class));
        Map<String, Object> emptyRating = jdbc.queryForMap("SELECT * FROM nri_tract_risk WHERE tract_fips = '12086000300'");
        assertNull(emptyRating.get("risk_rating"));
        assertEquals(0, new BigDecimal("0.021404").compareTo((BigDecimal) emptyRating.get("risk_score")),
            "a score next to an empty rating is kept");
    }

    @Test
    @DisplayName("Storm events: damage text in dollars, times from year-month/day/HHmm with the zone offset, unknown zone NULL")
    void stormEventsFixture() {
        LoadResult result = load(IngestionDataset.STORM_EVENTS, FIXTURES.resolve("12_storm_fixture.csv"), "12");

        assertEquals(4, result.rowsLoaded());
        assertEquals(1, result.rowsWithoutGeometry());
        Map<String, Object> flood = jdbc.queryForMap("SELECT *, ST_X(geom) AS lon, ST_Y(geom) AS lat FROM storm_events WHERE event_id = 1100001");
        assertEquals(0, new BigDecimal("1500000").compareTo((BigDecimal) flood.get("damage_property_usd")));
        assertEquals(0, new BigDecimal("25000").compareTo((BigDecimal) flood.get("damage_crops_usd")));
        assertEquals(OffsetDateTime.of(2024, 9, 10, 9, 30, 0, 0, ZoneOffset.ofHours(-5)).toInstant(),
            ((java.sql.Timestamp) flood.get("begin_at")).toInstant());
        assertEquals("12086", flood.get("fips5"));
        assertEquals(-80.295, ((Number) flood.get("lon")).doubleValue(), 1e-9);
        Map<String, Object> zone = jdbc.queryForMap("SELECT * FROM storm_events WHERE event_id = 1100002");
        assertNull(zone.get("fips5"));
        assertNull(zone.get("geom"));
        assertNull(zone.get("damage_property_usd"), "not reported");
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) zone.get("damage_crops_usd")), "a recorded zero");
        assertEquals(OffsetDateTime.of(2024, 9, 12, 0, 0, 0, 0, ZoneOffset.ofHours(-6)).toInstant(),
            ((java.sql.Timestamp) zone.get("end_at")).toInstant(), "2400 is midnight at the end of the day");
        Map<String, Object> tornado = jdbc.queryForMap("SELECT * FROM storm_events WHERE event_id = 1100003");
        assertNull(tornado.get("begin_at"), "an unknown time zone is not guessed");
        assertNull(tornado.get("episode_id"));
        assertEquals("F1", tornado.get("tor_f_scale"));
        assertEquals(0, new BigDecimal("500").compareTo((BigDecimal) jdbc.queryForObject(
            "SELECT damage_property_usd FROM storm_events WHERE event_id = 1100004", BigDecimal.class)));
        assertTrue(jdbc.queryForObject("""
            SELECT ST_DWithin(geom::geography, ST_SetSRID(ST_MakePoint(-80.295, 25.705), 4326)::geography, 1000)
            FROM storm_events WHERE event_id = 1100001""", Boolean.class));
    }

    @Test
    @DisplayName("Census tracts: polygons and multipolygons are stored as valid multipolygons; the state's tracts are replaced")
    void censusTractsFixture() {
        LoadResult result = load(IngestionDataset.CENSUS_TRACTS, FIXTURES.resolve("12_tracts_fixture.csv"), "12");
        load(IngestionDataset.CENSUS_TRACTS, FIXTURES.resolve("12_tracts_fixture.csv"), "12");

        assertEquals(3, result.rowsLoaded());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM census_tracts", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM census_tracts WHERE NOT ST_IsValid(geom) OR ST_IsEmpty(geom)", Integer.class));
        assertEquals("ST_MultiPolygon", jdbc.queryForObject("SELECT DISTINCT ST_GeometryType(geom) FROM census_tracts", String.class));
        assertEquals("12086000100", jdbc.queryForObject("""
            SELECT tract_fips FROM census_tracts
            WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(-80.29, 25.71), 4326))""", String.class));
    }
}
