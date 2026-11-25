package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.repository.JdbcSchoolScoreReader;
import com.geointelli.ai.property.service.repository.SchoolScoreReader;
import com.geointelli.ai.property.service.service.ReferenceDataImportService.LoadRequest;
import com.geointelli.ai.property.service.service.SchoolAssignmentService.AssignmentResult;
import com.geointelli.ai.property.service.service.SchoolScoreV2Service.ScoreResult;

/**
 * Opt-in checks of migration V49, the school assignment and school score v2 (SCRUM-203) on a real PostgreSQL +
 * PostGIS database, with the rules of SchoolReferenceDataDatabaseTest: a schema of its own on a dedicated local test
 * database whose name ends with _test, dropped afterwards; never the application's database. The SCRUM-202 fixture
 * (src/test/resources/school-data) gives the schools, grades and zones; each test property is a small square parcel.
 */
@EnabledIfEnvironmentVariable(named = "ENVIRONMENTAL_TEST_DB_URL", matches = ".+")
class SchoolScoringDatabaseTest {

    private static final String SCHEMA = "school_scoring_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final Path FIXTURES = Path.of("src/test/resources/school-data");
    private static final LocalDate BUILT = LocalDate.of(2026, 11, 22);
    private static final String MIAMI_DADE = "12086";
    private static final String ALACHUA = "12001";

    // property IDs and the centre of their parcel (lon, lat)
    /** Inside Coral Way part 1, the official and the NCES New Middle zones and the repaired Miami Senior High zone. */
    private static final long CORAL_WAY = 1;
    /** Inside the closed Old Town Elementary's zone and the NCES New Middle zone only, east of the high zone. */
    private static final long OLD_TOWN = 2;
    /** Inside Shadowlawn's zone, which has no school; north of every middle and high zone. */
    private static final long SHADOWLAWN = 3;
    /** In Alachua, inside Duval Elementary's zone. */
    private static final long GAINESVILLE = 4;
    /** Two parcels: a small one in Coral Way part 2 and a larger one outside every zone, which is used. */
    private static final long TWO_PARCELS = 5;
    /** A Miami-Dade property without a parcel. */
    private static final long NO_PARCEL = 6;
    /** fid of the larger parcel of TWO_PARCELS (the small one is 50, like every first parcel: ID * 10). */
    private static final long LARGE_PARCEL = 51;

    private static PGSimpleDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transaction;
    private static ReferenceDataImportServiceImpl importer;
    private static SchoolAssignmentServiceImpl assignments;
    private static SchoolScoreV2ServiceImpl scores;
    private static JdbcSchoolScoreReader reader;

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
        jdbc.execute("INSERT INTO states VALUES (1, 'FL', 'Florida')");
        jdbc.execute("INSERT INTO counties VALUES (1, 1, 'Miami-Dade'), (2, 1, 'Alachua')");
        // the columns the assignment reads; V49 references properties
        jdbc.execute("CREATE TABLE properties (id bigint PRIMARY KEY, county_id bigint REFERENCES counties(id))");
        jdbc.execute("CREATE TABLE parcels (fid bigint PRIMARY KEY, property_id bigint REFERENCES properties(id), "
            + "geom geometry(MultiPolygon, 4326))");
        // V42 gives counties.fips_code and reference_dataset_loads, V44 the school tables
        for (String migration : List.of("V42__add_environmental_reference_data.sql", "V44__add_school_reference_data.sql",
                "V49__add_property_school_assignments_and_scores.sql")) {
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration", migration)));
        }
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        importer = new ReferenceDataImportServiceImpl(dataSource, new ObjectMapper());
        assignments = new SchoolAssignmentServiceImpl(dataSource);
        scores = new SchoolScoreV2ServiceImpl(dataSource);
        reader = new JdbcSchoolScoreReader(jdbc);
    }

    @AfterAll
    static void cleanup() {
        // SCHEMA is generated here, never supplied by a caller; the database URL is checked above.
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE property_school_scores, property_school_assignments, parcels, properties, "
            + "school_attendance_zones, school_grades, schools, reference_dataset_loads RESTART IDENTITY CASCADE");
        load(IngestionDataset.SCHOOLS, "12_schools_fixture.csv");
        load(IngestionDataset.SCHOOL_GRADES, "12_grades_fixture.csv");
        load(IngestionDataset.SCHOOL_ZONES, "12_zones_fixture.csv");
        property(CORAL_WAY, 1, -80.215, 25.752);
        property(OLD_TOWN, 1, -80.199, 25.769);
        property(SHADOWLAWN, 1, -80.195, 25.835);
        property(GAINESVILLE, 2, -82.32, 29.66);
        property(TWO_PARCELS, 1, -80.245, 25.745);
        parcel(LARGE_PARCEL, TWO_PARCELS, -80.40, 25.60, 0.002);
        jdbc.update("INSERT INTO properties VALUES (?, 1)", NO_PARCEL);
    }

    private static void load(IngestionDataset dataset, String fixture) {
        Path file = FIXTURES.resolve(fixture);
        transaction.executeWithoutResult(status -> {
            try {
                importer.load(new LoadRequest(dataset, file, file.getFileName().toString(), "12", BUILT, null));
            } catch (java.io.IOException unreadable) {
                throw new java.io.UncheckedIOException(unreadable);
            }
        });
    }

    private static void property(long id, long countyId, double lon, double lat) {
        jdbc.update("INSERT INTO properties VALUES (?, ?)", id, countyId);
        parcel(id * 10, id, lon, lat, 0.0005);
    }

    private static void parcel(long fid, long propertyId, double lon, double lat, double halfSide) {
        jdbc.update("INSERT INTO parcels VALUES (?, ?, ST_Multi(ST_Expand(ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)))",
            fid, propertyId, lon, lat, halfSide);
    }

    private static AssignmentResult assign(String fips5) {
        return transaction.execute(status -> assignments.assignCounty(fips5));
    }

    private static ScoreResult score(String fips5) {
        return transaction.execute(status -> scores.scoreCounty(fips5));
    }

    /** level -> "school key (- without a school) / method / zone source type" of one property. */
    private static Map<String, String> assignmentsOf(long propertyId) {
        Map<String, String> rows = new java.util.TreeMap<>();
        jdbc.query("SELECT a.level, coalesce(s.school_key, '-') AS school_key, a.method, a.zone_source_type "
            + "FROM property_school_assignments a LEFT JOIN schools s ON s.id = a.school_id WHERE a.property_id = ?", rs -> {
                rows.put(rs.getString("level"), rs.getString("school_key") + " / " + rs.getString("method")
                    + (rs.getString("zone_source_type") == null ? "" : " / " + rs.getString("zone_source_type")));
            }, propertyId);
        return rows;
    }

    private static long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    @DisplayName("V49 creates the two tables with their keys and indexes, and refuses an inconsistent row")
    void migration() {
        List<String> indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = ? ORDER BY 1",
            String.class, SCHEMA);
        for (String index : List.of("property_school_assignments_pkey", "idx_property_school_assignments_school",
                "idx_property_school_assignments_zone", "property_school_scores_pkey")) {
            assertTrue(indexes.contains(index), index);
        }
        Long school = jdbc.queryForObject("SELECT id FROM schools WHERE school_key = '13-0041'", Long.class);
        // a NEAREST row has no zone; a label needs a score; parts stay within 0-100
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO property_school_assignments VALUES "
            + "(1, 'ELEMENTARY', ?, 'NEAREST', NULL, 'OFFICIAL', 10, now())", school));
        // only UNMATCHED_ZONE has no school, and then no distance either
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO property_school_assignments VALUES "
            + "(1, 'ELEMENTARY', NULL, 'ZONE', NULL, 'OFFICIAL', NULL, now())"));
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO property_school_assignments VALUES "
            + "(1, 'ELEMENTARY', ?, 'UNMATCHED_ZONE', NULL, 'OFFICIAL', 10, now())", school));
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO property_school_assignments VALUES "
            + "(1, 'ELEMENTARY', NULL, 'UNMATCHED_ZONE', NULL, 'OFFICIAL', 10, now())"));
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO property_school_scores VALUES "
            + "(1, NULL, NULL, NULL, NULL, 'Good', 2026, 2, now())"));
        assertThrows(Exception.class, () -> jdbc.update("INSERT INTO property_school_scores VALUES "
            + "(1, 120, NULL, NULL, 90, 'Excellent', 2026, 2, now())"));
    }

    @Test
    @DisplayName("Each level gets its zone's active school, no school when the zone has none, or the nearest zoned "
        + "public school when no zone contains it; closed schools never")
    void assignment() {
        AssignmentResult result = assign(MIAMI_DADE);

        // 4 Miami-Dade properties with a parcel; the one without a parcel and the Alachua one are not touched
        assertEquals(4, result.properties());
        assertEquals(4, result.assignedProperties());
        assertEquals(Map.of("ELEMENTARY", "13-0041 / ZONE / OFFICIAL", "MIDDLE", "12086-NEW MIDDLE / ZONE / OFFICIAL",
            "HIGH", "13-7071 / ZONE / OFFICIAL"), assignmentsOf(CORAL_WAY), "the official middle zone wins over NCES");
        assertEquals(Map.of("ELEMENTARY", "13-0041 / NEAREST", "MIDDLE", "12086-NEW MIDDLE / ZONE / NCES_SABS_2015_16",
            "HIGH", "13-7071 / NEAREST"), assignmentsOf(OLD_TOWN), "Old Town is closed: the nearest school instead");
        // Shadowlawn's zone has no school: no school for that level, never the nearest one; no public middle school
        // has a zone, so no middle row at all
        assertEquals(Map.of("ELEMENTARY", "- / UNMATCHED_ZONE / OFFICIAL", "HIGH", "13-7071 / NEAREST"),
            assignmentsOf(SHADOWLAWN));
        assertNull(jdbc.queryForObject("SELECT distance_m FROM property_school_assignments WHERE property_id = ? "
            + "AND level = 'ELEMENTARY'", BigDecimal.class, SHADOWLAWN));
        assertEquals(Map.of("ELEMENTARY", "13-0041 / NEAREST", "HIGH", "13-7071 / NEAREST"), assignmentsOf(TWO_PARCELS),
            "the larger parcel, outside every zone, is used");
        assertTrue(assignmentsOf(GAINESVILLE).isEmpty());
        assertTrue(assignmentsOf(NO_PARCEL).isEmpty());
        assertEquals(0, count("SELECT count(*) FROM property_school_assignments a JOIN schools s ON s.id = a.school_id "
            + "WHERE NOT s.is_active"));
        assertEquals(2, result.rows("ELEMENTARY", "NEAREST"));
        assertEquals(1, result.rows("ELEMENTARY", "UNMATCHED_ZONE"));
        assertEquals(1, result.rows("ELEMENTARY", "ZONE"));
        assertEquals(10, result.inserted(), "3 + 3 + 2 + 2 rows");
        // distances in metres on the spheroid: Coral Way K-8 is about 430 m from the Coral Way parcel
        BigDecimal meters = jdbc.queryForObject("SELECT distance_m FROM property_school_assignments "
            + "WHERE property_id = ? AND level = 'ELEMENTARY'", BigDecimal.class, CORAL_WAY);
        assertTrue(meters.doubleValue() > 380 && meters.doubleValue() < 480, meters.toString());
    }

    @Test
    @DisplayName("Running again writes nothing; a changed parcel updates only its rows; another county is untouched")
    void rerunAndChanges() {
        assign(MIAMI_DADE);
        AssignmentResult gainesville = assign(ALACHUA);
        assertEquals(Map.of("ELEMENTARY", "01-0021 / ZONE / OFFICIAL"), assignmentsOf(GAINESVILLE));
        assertEquals(1, gainesville.inserted());

        AssignmentResult again = assign(MIAMI_DADE);
        assertEquals(0, again.inserted() + again.updated() + again.deleted());

        // the large parcel goes: the small one in Coral Way part 2 is now the property's point
        jdbc.update("DELETE FROM parcels WHERE fid = ?", LARGE_PARCEL);
        // and Shadowlawn's property loses its parcel
        jdbc.update("DELETE FROM parcels WHERE property_id = ?", SHADOWLAWN);
        AssignmentResult changed = assign(MIAMI_DADE);

        assertEquals(Map.of("ELEMENTARY", "13-0041 / ZONE / OFFICIAL", "MIDDLE", "12086-NEW MIDDLE / ZONE / NCES_SABS_2015_16",
            "HIGH", "13-7071 / ZONE / OFFICIAL"), assignmentsOf(TWO_PARCELS));
        assertTrue(assignmentsOf(SHADOWLAWN).isEmpty());
        assertEquals(1, changed.inserted(), "the new middle row");
        assertEquals(2, changed.updated(), "elementary and high moved from NEAREST to ZONE");
        assertEquals(2, changed.deleted(), "Shadowlawn's two rows");
        assertEquals(Map.of("ELEMENTARY", "01-0021 / ZONE / OFFICIAL"), assignmentsOf(GAINESVILLE));
    }

    @Test
    @DisplayName("A zones reload keeps the rows with their source type, and a schools reload can delete an assigned school")
    void reloadsAreNotBlocked() throws Exception {
        assign(MIAMI_DADE);

        load(IngestionDataset.SCHOOL_ZONES, "12_zones_fixture.csv");
        assertEquals(0, count("SELECT count(*) FROM property_school_assignments WHERE zone_id IS NOT NULL"));
        // Coral Way's three levels, Old Town's middle school and Shadowlawn's school-less elementary zone
        assertEquals(5, count("SELECT count(*) FROM property_school_assignments WHERE zone_source_type IS NOT NULL"));
        AssignmentResult relinked = assign(MIAMI_DADE);
        assertEquals(5, relinked.updated(), "the ZONE and UNMATCHED_ZONE rows get the new zone ids");
        assertEquals(0, count("SELECT count(*) FROM property_school_assignments WHERE method = 'ZONE' AND zone_id IS NULL"));

        // a schools file without Miami Senior High deletes it, and its assignments with it
        Path withoutMiamiSenior = Files.createTempFile("schools", ".csv");
        try {
            Files.write(withoutMiamiSenior, Files.readAllLines(FIXTURES.resolve("12_schools_fixture.csv")).stream()
                .filter(line -> !line.startsWith("13-7071,")).toList());
            transaction.executeWithoutResult(status -> {
                try {
                    importer.load(new LoadRequest(IngestionDataset.SCHOOLS, withoutMiamiSenior, "schools.csv", "12",
                        BUILT, null));
                } catch (java.io.IOException unreadable) {
                    throw new java.io.UncheckedIOException(unreadable);
                }
            });
        } finally {
            Files.deleteIfExists(withoutMiamiSenior);
        }
        assertEquals(0, count("SELECT count(*) FROM property_school_assignments WHERE level = 'HIGH'"));
    }

    @Test
    @DisplayName("Score v2 uses the grades, distances and choices; unknown parts are left out; a rerun writes nothing")
    void scoreV2() {
        // Coral Way and Miami Senior are alone in their FLDOE type in the fixture, so they have no percentile yet
        jdbc.update("UPDATE school_grades SET statewide_percentile = 80 WHERE state_school_id = '13-0041' AND school_year = 2026");
        jdbc.update("UPDATE school_grades SET statewide_percentile = 60 WHERE state_school_id = '13-7071' AND school_year = 2026");
        assign(MIAMI_DADE);

        ScoreResult result = score(MIAMI_DADE);

        assertEquals(2026, result.gradeYear());
        assertEquals(4, result.properties());
        assertEquals(4, result.scored());
        assertEquals(4, result.inserted());
        Map<String, Object> coralWay = jdbc.queryForMap("SELECT * FROM property_school_scores WHERE property_id = ?",
            CORAL_WAY);
        // quality: Coral Way 80 (40%) and Miami Senior 60 (30%); New Middle has no grade: (32 + 18) / 0.7
        assertEquals(new BigDecimal("71.43"), coralWay.get("quality_score"));
        // access: 430 m (100), New Middle about 3.4 miles (50), Miami Senior about 1.5 miles (75)
        assertEquals(new BigDecimal("77.50"), coralWay.get("access_score"));
        // choice: no other elementary or high school within 3 miles (0); for middle, Coral Way K-8 (80)
        assertEquals(new BigDecimal("24.00"), coralWay.get("choice_score"));
        // 0.60 * 71.43 + 0.25 * 77.5 + 0.15 * 24
        assertEquals(new BigDecimal("65.83"), coralWay.get("school_score"));
        assertEquals("Good", coralWay.get("label"));
        // smallint columns come back as Integer from PgJDBC
        assertEquals(2026, coralWay.get("grade_year"));
        assertEquals(2, coralWay.get("score_version"));

        ScoreResult again = score(MIAMI_DADE);
        assertEquals(0, again.inserted() + again.updated() + again.deleted());
        assertEquals(0, count("SELECT count(*) FROM property_school_scores WHERE property_id = ?", GAINESVILLE));

        // a new percentile changes only the properties whose quality or choice uses it
        jdbc.update("UPDATE school_grades SET statewide_percentile = 90 WHERE state_school_id = '13-7071' AND school_year = 2026");
        ScoreResult regraded = score(MIAMI_DADE);
        assertEquals(4, regraded.updated());
        assertEquals(0, regraded.inserted());
    }

    @Test
    @DisplayName("The reader returns the assigned schools with their grade of the score's year, and the score")
    void reader() {
        jdbc.update("UPDATE school_grades SET statewide_percentile = 80 WHERE state_school_id = '13-0041' AND school_year = 2026");
        assign(MIAMI_DADE);

        // before any score: the grade of the newest graded year
        Map<Long, List<SchoolScoreReader.AssignedSchool>> before = reader.assignedSchools(List.of(CORAL_WAY));
        assertEquals(List.of("ELEMENTARY", "MIDDLE", "HIGH"),
            before.get(CORAL_WAY).stream().map(SchoolScoreReader.AssignedSchool::level).toList());
        SchoolScoreReader.AssignedSchool coralWay = before.get(CORAL_WAY).get(0);
        assertEquals("CORAL WAY K-8 CENTER", coralWay.name());
        assertEquals(2026, coralWay.gradeYear());
        assertEquals("A", coralWay.letterGrade());
        assertEquals(80, coralWay.statewidePercentile());
        assertEquals("OFFICIAL", coralWay.zoneSourceType());
        SchoolScoreReader.AssignedSchool newMiddle = before.get(CORAL_WAY).get(1);
        assertNull(newMiddle.fldoeGrade(), "New Middle is not in the FLDOE file");
        assertTrue(reader.scores(List.of(CORAL_WAY)).isEmpty());
        // a zone without a school: the zone's name, no school, no distance
        SchoolScoreReader.AssignedSchool shadowlawn = reader.assignedSchools(List.of(SHADOWLAWN)).get(SHADOWLAWN).get(0);
        assertEquals("UNMATCHED_ZONE", shadowlawn.method());
        assertNull(shadowlawn.schoolId());
        assertEquals("Shadowlawn Elementary", shadowlawn.name());
        assertNull(shadowlawn.distanceM());

        score(MIAMI_DADE);
        SchoolScoreReader.Score stored = reader.scores(List.of(CORAL_WAY, NO_PARCEL)).get(CORAL_WAY);
        assertNotNull(stored);
        assertEquals(2, stored.scoreVersion());
        assertNotNull(stored.computedAt());
        assertFalse(reader.scores(List.of(CORAL_WAY, NO_PARCEL)).containsKey(NO_PARCEL));
        assertTrue(reader.available());
    }
}
