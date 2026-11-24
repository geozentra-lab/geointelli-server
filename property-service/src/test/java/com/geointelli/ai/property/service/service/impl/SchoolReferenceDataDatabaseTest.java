package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Opt-in checks of migration V44 and the school loads (SCRUM-202) on a real PostgreSQL + PostGIS database (H2 has
 * no PostGIS), with the same rules as ReferenceDataImportDatabaseTest: a schema of its own on a dedicated local test
 * database whose name ends with _test, dropped afterwards; never the application's database.
 * Fixture: src/test/resources/school-data. The lookup queries are those of docs/school-reference-data.md, with a
 * fixed point instead of a parcel.
 */
@EnabledIfEnvironmentVariable(named = "ENVIRONMENTAL_TEST_DB_URL", matches = ".+")
class SchoolReferenceDataDatabaseTest {

    private static final String SCHEMA = "school_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final Path FIXTURES = Path.of("src/test/resources/school-data");
    private static final LocalDate BUILT = LocalDate.of(2026, 11, 22);

    /** Inside Coral Way part 1, both New Middle zones and the repaired Miami Senior High bow tie. */
    private static final double[] CORAL_WAY = {-80.215, 25.752};
    /** Inside Shadowlawn's zone, which has no school. */
    private static final double[] SHADOWLAWN = {-80.195, 25.835};

    /** Query (a) of the docs: the zones containing a point, each with its school and that school's latest grade row. */
    static final String ZONES_AT_POINT = """
        WITH p AS (SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326) AS pt)
        SELECT z.id AS zone_id, z.level, z.source_type, z.zone_year, z.school_id, s.name, s.sector, s.is_active,
            g.school_year, g.letter_grade, g.fldoe_grade, g.pct_total_points, g.statewide_percentile
        FROM p
        JOIN school_attendance_zones z ON ST_Intersects(z.geom, p.pt)
        LEFT JOIN schools s ON s.id = z.school_id
        LEFT JOIN LATERAL (
            SELECT gr.school_year, gr.letter_grade, gr.fldoe_grade, gr.pct_total_points, gr.statewide_percentile
            FROM school_grades gr WHERE gr.state_school_id = s.state_school_id
            ORDER BY gr.school_year DESC LIMIT 1) g ON true
        ORDER BY z.level,
            array_position(ARRAY['OFFICIAL', 'OFFICIAL_RECONSTRUCTED', 'COUNTY_WIDE_SINGLE_SCHOOL', 'NCES_SABS_2015_16',
                'UNOFFICIAL'], z.source_type),
            ST_Area(z.geom)""";
    /** Query (b): the nearest active schools of one level and sector. */
    static final String NEAREST = """
        WITH p AS (SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography AS pt)
        SELECT s.id, s.name, s.level, s.sector, round(ST_Distance(s.geom::geography, p.pt)) AS meters
        FROM p CROSS JOIN LATERAL (
            SELECT * FROM schools s
            WHERE s.is_active AND s.level = ? AND s.sector = ?
            ORDER BY s.geom::geography <-> p.pt
            LIMIT 3) s
        ORDER BY meters""";
    /** Query (c): the active schools within a radius in metres. */
    static final String WITHIN = """
        WITH p AS (SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography AS pt)
        SELECT s.id, s.name, s.level, s.sector, round(ST_Distance(s.geom::geography, p.pt)) AS meters
        FROM p JOIN schools s ON ST_DWithin(s.geom::geography, p.pt, ?)
        WHERE s.is_active
        ORDER BY meters""";
    /** Query (d): the data vintage, the latest load of each school dataset. */
    static final String VINTAGE = """
        SELECT DISTINCT ON (dataset) dataset, source, downloaded_on, file, rows_loaded, loaded_at
        FROM reference_dataset_loads
        WHERE dataset IN ('SCHOOLS', 'SCHOOL_GRADES', 'SCHOOL_ZONES')
        ORDER BY dataset, loaded_at DESC, id DESC""";

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
        jdbc.execute("INSERT INTO states VALUES (1, 'FL', 'Florida')");
        jdbc.execute("INSERT INTO counties VALUES (1, 1, 'Miami-Dade'), (2, 1, 'Alachua')");
        // V44 needs reference_dataset_loads from V42.
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V42__add_environmental_reference_data.sql")));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V44__add_school_reference_data.sql")));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        importer = new ReferenceDataImportServiceImpl(dataSource, new ObjectMapper());
    }

    @AfterAll
    static void cleanup() {
        // SCHEMA is generated here, never supplied by a caller; the database URL is checked above.
        if (jdbc != null) jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }

    @BeforeEach
    void empty() {
        jdbc.execute("TRUNCATE school_attendance_zones, school_grades, schools, reference_dataset_loads RESTART IDENTITY CASCADE");
    }

    private static LoadResult load(IngestionDataset dataset, Path file) {
        return transaction.execute(status -> {
            try {
                return importer.load(new LoadRequest(dataset, file, file.getFileName().toString(), "12", BUILT, null));
            } catch (java.io.IOException unreadable) {
                throw new java.io.UncheckedIOException(unreadable);
            }
        });
    }

    private static void loadAll() {
        load(IngestionDataset.SCHOOLS, FIXTURES.resolve("12_schools_fixture.csv"));
        load(IngestionDataset.SCHOOL_GRADES, FIXTURES.resolve("12_grades_fixture.csv"));
        load(IngestionDataset.SCHOOL_ZONES, FIXTURES.resolve("12_zones_fixture.csv"));
    }

    private Path write(String name, List<String> lines) throws Exception {
        Path file = temp.resolve(name);
        Files.write(file, lines);
        return file;
    }

    /** The fixture file with only its header and the lines matching a filter. */
    private Path subset(String fixture, String name, java.util.function.Predicate<String> keep) throws Exception {
        List<String> lines = Files.readAllLines(FIXTURES.resolve(fixture));
        List<String> kept = new java.util.ArrayList<>(List.of(lines.get(0)));
        lines.stream().skip(1).filter(keep).forEach(kept::add);
        return write(name, kept);
    }

    private static Long schoolId(String key) {
        return jdbc.queryForObject("SELECT id FROM schools WHERE school_key = ?", Long.class, key);
    }

    @Test
    @DisplayName("V44 creates the three tables with their spatial and lookup indexes")
    void migrationCreatesTablesAndIndexes() {
        List<String> indexes = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = ? ORDER BY 1",
            String.class, SCHEMA);
        for (String index : List.of("idx_schools_geom", "idx_schools_geog", "idx_schools_fips5", "ux_schools_state_school_id",
                "idx_school_grades_year_type", "idx_school_attendance_zones_geom", "idx_school_attendance_zones_fips5",
                "idx_school_attendance_zones_school")) {
            assertTrue(indexes.contains(index), index);
        }
        assertEquals("gist", jdbc.queryForObject("SELECT am.amname FROM pg_class c JOIN pg_am am ON am.oid = c.relam "
            + "WHERE c.relname = 'idx_school_attendance_zones_geom' AND c.relnamespace = ?::regnamespace", String.class, SCHEMA));
    }

    @Test
    @DisplayName("A full load stores every row, links zones to schools, repairs the invalid polygon and records the vintage")
    void fullLoad() {
        LoadResult schools = load(IngestionDataset.SCHOOLS, FIXTURES.resolve("12_schools_fixture.csv"));
        LoadResult grades = load(IngestionDataset.SCHOOL_GRADES, FIXTURES.resolve("12_grades_fixture.csv"));
        LoadResult zones = load(IngestionDataset.SCHOOL_ZONES, FIXTURES.resolve("12_zones_fixture.csv"));

        assertEquals(5, schools.rowsLoaded());
        assertEquals(10, grades.rowsLoaded());
        assertEquals(8, zones.rowsLoaded());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM school_attendance_zones WHERE NOT ST_IsValid(geom)", Long.class));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT ST_IsValid(ST_GeomFromText(?, 4326))", Boolean.class,
            "MULTIPOLYGON(((-80.25 25.73,-80.20 25.78,-80.20 25.73,-80.25 25.78,-80.25 25.73)))"), "the bow tie is invalid in the file");
        assertEquals(2, jdbc.queryForObject("SELECT ST_NumGeometries(geom) FROM school_attendance_zones WHERE zone_school_name = "
            + "'Miami Senior High'", Integer.class), "the repaired bow tie is two triangles");

        assertEquals(schoolId("13-0041"), jdbc.queryForObject("SELECT school_id FROM school_attendance_zones "
            + "WHERE zone_school_name = 'Coral Way K-8'", Long.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM school_attendance_zones WHERE school_id = ? AND level = 'ELEMENTARY'",
            Integer.class, schoolId("13-0041")), "one school, two zone rows at one level");
        assertNull(jdbc.queryForObject("SELECT school_id FROM school_attendance_zones WHERE zone_school_name = "
            + "'Shadowlawn Elementary'", Long.class), "zone without a school");
        assertNull(jdbc.queryForObject("SELECT state_school_id FROM schools WHERE school_key = '12086-NEW MIDDLE'", String.class));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT is_active FROM schools WHERE school_key = '13-0999'", Boolean.class),
            "the closed school is kept, inactive");
        assertNull(jdbc.queryForObject("SELECT is_virtual FROM schools WHERE school_key = '12086-NEW MIDDLE'", Boolean.class));
        assertEquals("POINT(-80.217711 25.755112)", jdbc.queryForObject("SELECT ST_AsText(geom) FROM schools WHERE "
            + "school_key = '13-0041'", String.class));

        List<Map<String, Object>> vintage = jdbc.queryForList(VINTAGE);
        assertEquals(java.util.Set.of("SCHOOLS", "SCHOOL_GRADES", "SCHOOL_ZONES"),
            vintage.stream().map(r -> r.get("dataset")).collect(java.util.stream.Collectors.toSet()));
        assertTrue(vintage.stream().allMatch(r -> r.get("downloaded_on").toString().equals("2026-11-22")));
    }

    @Test
    @DisplayName("Grades: I and N give no letter, unknown values are NULL, and percentiles rank only within type and year")
    void gradesAndPercentiles() {
        loadAll();
        Map<String, Object> incomplete = jdbc.queryForMap("SELECT * FROM school_grades WHERE state_school_id = '13-0060'");
        assertNull(incomplete.get("letter_grade"), "I is not a low grade");
        assertEquals("I", incomplete.get("fldoe_grade"));
        assertNull(incomplete.get("pct_total_points"), "unknown, never 0");
        assertNull(incomplete.get("statewide_percentile"));
        assertNull(jdbc.queryForObject("SELECT letter_grade FROM school_grades WHERE school_year = 2003", String.class));

        // Elementary 2026: 50, 60, 70 points -> percentiles 0, 50, 100.
        assertEquals(0, percentile("13-0999", 2026));
        assertEquals(50, percentile("13-0050", 2026));
        assertEquals(100, percentile("01-0021", 2026));
        assertNull(percentile("13-7071", 2026), "the only high school has no rank");
        assertNull(percentile("13-0041", 2026), "the only combination school has no rank");
        assertNull(percentile("13-0041", 2025), "old years have only the letter");
        assertNull(jdbc.queryForObject("SELECT graduation_rate FROM school_grades WHERE state_school_id = '13-0041' "
            + "AND school_year = 2026", Object.class), "a component that does not apply is NULL");
    }

    private static Integer percentile(String school, int year) {
        return jdbc.queryForObject("SELECT statewide_percentile FROM school_grades WHERE state_school_id = ? AND school_year = ?",
            Integer.class, school, year);
    }

    @Test
    @DisplayName("Zones loaded before their schools are linked when the schools are loaded")
    void zonesBeforeSchools() {
        load(IngestionDataset.SCHOOL_ZONES, FIXTURES.resolve("12_zones_fixture.csv"));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM school_attendance_zones WHERE school_id IS NOT NULL", Long.class));

        load(IngestionDataset.SCHOOLS, FIXTURES.resolve("12_schools_fixture.csv"));

        assertEquals(7L, jdbc.queryForObject("SELECT count(*) FROM school_attendance_zones WHERE school_id IS NOT NULL", Long.class),
            "every zone with a known school_key");
    }

    @Test
    @DisplayName("Re-importing one county replaces its schools and zones without duplicates; ids stay; other counties stay")
    void reimportOneCounty() throws Exception {
        loadAll();
        Long coralWay = schoolId("13-0041");
        // Miami-Dade again, without the closed school and with Coral Way renamed.
        Path miamiDade = subset("12_schools_fixture.csv", "12_schools_miami_dade.csv",
            line -> line.contains(",12086,") && !line.startsWith("13-0999"));
        Files.writeString(miamiDade, Files.readString(miamiDade).replace("CORAL WAY K-8 CENTER", "CORAL WAY K-8"));

        load(IngestionDataset.SCHOOLS, miamiDade);
        load(IngestionDataset.SCHOOLS, miamiDade);

        assertEquals(3L, jdbc.queryForObject("SELECT count(*) FROM schools WHERE fips5 = '12086'", Long.class));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM schools WHERE fips5 = '12001'", Long.class), "Alachua untouched");
        assertEquals(coralWay, schoolId("13-0041"), "the school keeps its id");
        assertEquals("CORAL WAY K-8", jdbc.queryForObject("SELECT name FROM schools WHERE id = ?", String.class, coralWay));
        assertNull(jdbc.queryForObject("SELECT school_id FROM school_attendance_zones WHERE zone_school_name = "
            + "'Old Town Elementary'", Long.class), "the dropped school's zone stays, without a school");

        Path alachuaZones = subset("12_zones_fixture.csv", "12_zones_alachua.csv", line -> line.startsWith("12001,"));
        load(IngestionDataset.SCHOOL_ZONES, alachuaZones);
        load(IngestionDataset.SCHOOL_ZONES, alachuaZones);

        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM school_attendance_zones WHERE fips5 = '12001'", Long.class));
        assertEquals(7L, jdbc.queryForObject("SELECT count(*) FROM school_attendance_zones WHERE fips5 = '12086'", Long.class),
            "Miami-Dade zones untouched");
    }

    @Test
    @DisplayName("Re-importing one grade year replaces that year only, without duplicates")
    void reimportOneYear() throws Exception {
        loadAll();
        Path year2025 = subset("12_grades_fixture.csv", "12_grades_2025.csv", line -> line.contains(",2025,"));
        Files.writeString(year2025, Files.readString(year2025).replace("2025,C,", "2025,B,"));

        load(IngestionDataset.SCHOOL_GRADES, year2025);
        load(IngestionDataset.SCHOOL_GRADES, year2025);

        assertEquals(3L, jdbc.queryForObject("SELECT count(*) FROM school_grades WHERE school_year = 2025", Long.class));
        assertEquals("B", jdbc.queryForObject("SELECT letter_grade FROM school_grades WHERE state_school_id = '13-7071' "
            + "AND school_year = 2025", String.class));
        assertEquals(6L, jdbc.queryForObject("SELECT count(*) FROM school_grades WHERE school_year = 2026", Long.class));
        assertEquals(100, percentile("01-0021", 2026), "the 2026 ranks are kept");
    }

    @Test
    @DisplayName("A file with a duplicate key or a malformed row is refused and leaves the previous rows as they were")
    void badFileRollsBack() throws Exception {
        loadAll();
        List<String> lines = Files.readAllLines(FIXTURES.resolve("12_schools_fixture.csv"));
        Path duplicate = write("duplicate.csv", List.of(lines.get(0), lines.get(1), lines.get(1)));
        Path malformed = write("malformed.csv", List.of(lines.get(0), lines.get(1).replace(",true,1215,", ",maybe,1215,")));

        assertThrows(InvalidReferenceRowException.class, () -> load(IngestionDataset.SCHOOLS, duplicate));
        var row = assertThrows(InvalidReferenceRowException.class, () -> load(IngestionDataset.SCHOOLS, malformed));

        assertEquals(1, row.row());
        assertEquals(5L, jdbc.queryForObject("SELECT count(*) FROM schools", Long.class));
        assertEquals(3L, jdbc.queryForObject("SELECT count(*) FROM reference_dataset_loads", Long.class), "no load recorded");
    }

    @Test
    @DisplayName("Lookup (a): the zones at a point, best source first, each with its school and latest grade")
    void zonesAtPoint() {
        loadAll();
        List<Map<String, Object>> zones = jdbc.queryForList(ZONES_AT_POINT, CORAL_WAY[0], CORAL_WAY[1]);

        assertEquals(List.of("ELEMENTARY", "HIGH", "MIDDLE", "MIDDLE"), zones.stream().map(z -> z.get("level")).toList());
        assertEquals("CORAL WAY K-8 CENTER", zones.get(0).get("name"));
        assertEquals("A", zones.get(0).get("letter_grade"));
        assertEquals((short) 2026, ((Number) zones.get(0).get("school_year")).shortValue());
        assertEquals("MIAMI SENIOR HIGH SCHOOL", zones.get(1).get("name"), "inside the repaired bow tie");
        assertEquals("OFFICIAL", zones.get(2).get("source_type"), "the official zone before the 2015-16 NCES one");
        assertEquals("NCES_SABS_2015_16", zones.get(3).get("source_type"));

        List<Map<String, Object>> noSchool = jdbc.queryForList(ZONES_AT_POINT, SHADOWLAWN[0], SHADOWLAWN[1]);
        assertEquals(1, noSchool.size());
        assertNull(noSchool.get(0).get("school_id"), "the caller falls back to the nearest school");
    }

    @Test
    @DisplayName("Lookups (b) and (c): nearest active schools of a level and sector, and active schools within a radius")
    void nearestAndRadius() {
        loadAll();
        List<Map<String, Object>> nearest = jdbc.queryForList(NEAREST, SHADOWLAWN[0], SHADOWLAWN[1], "ELEMENTARY", "PUBLIC");
        assertEquals("CORAL WAY K-8 CENTER", nearest.get(0).get("name"), "the closed school is never offered");
        assertFalse(nearest.stream().anyMatch(s -> "OLD TOWN ELEMENTARY".equals(s.get("name"))));

        List<Map<String, Object>> within = jdbc.queryForList(WITHIN, CORAL_WAY[0], CORAL_WAY[1], 3000);
        assertEquals(List.of("CORAL WAY K-8 CENTER", "MIAMI SENIOR HIGH SCHOOL"), within.stream().map(s -> s.get("name")).toList());
        assertNotNull(within.get(0).get("meters"));
        assertTrue(((Number) within.get(1).get("meters")).doubleValue() < 3000);
    }
}
