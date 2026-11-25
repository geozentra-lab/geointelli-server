package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.impl.ReferenceDataRows.RowMapper;

/**
 * Normalisation and SQL of the school reference loads (SCRUM-202): unknown source values are null, never 0 or a low
 * grade; the PostGIS behaviour is checked by SchoolReferenceDataDatabaseTest.
 */
class SchoolReferenceDataRowsTest {

    static final String SCHOOLS_HEADER = "school_key,state_school_id,nces_id,fips5,county,name,sector,level,school_type,"
        + "lowest_grade,highest_grade,is_virtual,is_active,enrollment,free_reduced_lunch_pct,address,city,zip,latitude,"
        + "longitude,in_schools_zip,status_note";
    static final String GRADES_HEADER = "state_school_id,district_number,school_number,school_name,school_year,"
        + "letter_grade,is_baseline,fldoe_school_type,is_charter,is_title_i,alternative_ese_center,grade3_ela_achievement,"
        + "ela_achievement,ela_learning_gains,ela_lowest25_gains,math_achievement,math_learning_gains,math_lowest25_gains,"
        + "science_achievement,social_studies_achievement,middle_school_acceleration,graduation_rate,"
        + "college_career_acceleration,total_points_earned,total_components,pct_total_points,pct_tested,pct_econ_disadvantaged";
    static final String ZONES_HEADER = "fips5,county,level,zone_school_name,school_key,school_match,source_school_id,"
        + "source,source_type,zone_year,method_fr,note_fr,geom_wkt";

    private static final String SCHOOL = "13-0041,13-0041,120039000412,12086,Miami-Dade,CORAL WAY K-8 CENTER,PUBLIC,"
        + "ELEMENTARY,REGULAR,-1,8,false,true,1215,64.5,1950 SW 13TH AVE,MIAMI,33145,25.755112,-80.217711,false,"
        + "Listed in NCES 2024-25";
    private static final String GRADE_2026 = "13-0041,13,41,CORAL WAY K-8 CENTER,2026,A,false,Combination,false,true,No,"
        + "71,68,60,55,72,66,59,61,74,,,,689,10,69,99,58";

    private static CSVRecord record(String header, String row) throws IOException {
        CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
            .parse(new StringReader(header + "\n" + row + "\n"));
        return parser.iterator().next();
    }

    /** The mapped row as column name -> value. */
    private static java.util.Map<String, String> map(IngestionDataset dataset, String header, String row) throws IOException {
        RowMapper mapper = ReferenceDataRows.forDataset(dataset, "12", Arrays.asList(header.split(",")), new ObjectMapper());
        List<String> values = mapper.map(record(header, row));
        assertEquals(mapper.columns().size(), values.size(), "one value per staging column");
        java.util.Map<String, String> byName = new java.util.LinkedHashMap<>();
        for (int i = 0; i < values.size(); i++) byName.put(mapper.columns().get(i).name(), values.get(i));
        return byName;
    }

    private static String replace(String row, int index, String value) {
        List<String> fields = new ArrayList<>(Arrays.asList(row.split(",", -1)));
        fields.set(index, value == null ? "" : value);
        return String.join(",", fields);
    }

    @Nested
    @DisplayName("schools.csv")
    class Schools {

        @Test
        @DisplayName("A school maps with its point, grade range, booleans and the source row")
        void mapsASchool() throws IOException {
            var values = map(IngestionDataset.SCHOOLS, SCHOOLS_HEADER, SCHOOL);

            assertEquals("1", values.get("source_row"));
            assertEquals("13-0041", values.get("state_school_id"));
            assertEquals("-1", values.get("lowest_grade"), "-1 = Pre-K");
            assertEquals("8", values.get("highest_grade"));
            assertEquals("true", values.get("is_active"));
            assertEquals("false", values.get("is_virtual"));
            assertEquals("64.5", values.get("free_reduced_lunch_pct"));
            assertEquals("25.755112", values.get("latitude"));
            assertEquals("-80.217711", values.get("longitude"));
        }

        @Test
        @DisplayName("A new school without a state id keeps its key; empty sector, level, type and virtual flag are NULL")
        void newSchoolWithoutStateId() throws IOException {
            String row = "nces-HCPS-MORGAN,,HCPS-MORGAN,12057,Hillsborough,MORGAN HIGH SCHOOL,,,,,,,true,,,1 MAIN ST,"
                + "TAMPA,,27.95,-82.45,false,";
            var values = map(IngestionDataset.SCHOOLS, SCHOOLS_HEADER, row);

            assertEquals("nces-HCPS-MORGAN", values.get("school_key"));
            assertNull(values.get("state_school_id"));
            assertNull(values.get("sector"));
            assertNull(values.get("level"));
            assertNull(values.get("school_type"));
            assertNull(values.get("is_virtual"), "unknown, not false");
            assertNull(values.get("enrollment"), "unknown, not 0");
            assertNull(values.get("lowest_grade"));
            assertNull(values.get("zip"));
        }

        @Test
        @DisplayName("A closed school is kept and flagged inactive")
        void closedSchoolIsInactive() throws IOException {
            var values = map(IngestionDataset.SCHOOLS, SCHOOLS_HEADER, replace(SCHOOL, 12, "false"));

            assertEquals("false", values.get("is_active"));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
            "state id not DD-SSSS|1|13041",
            "county of another state|3|13121",
            "unknown sector|6|PRIVATE",
            "unknown level|7|K8",
            "grade below Pre-K|9|-2",
            "lowest above highest grade|9|9",
            "flag not true/false|12|yes",
            "missing active flag|12|",
            "negative enrollment|13|-5",
            "lunch share above 100|14|101",
            "not a ZIP code|17|3314",
            "missing latitude|18|",
            "longitude out of range|19|-181" })
        @DisplayName("A malformed school value rejects the row")
        void rejectsMalformedValues(String reason, int column, String value) {
            assertThrows(IllegalArgumentException.class,
                () -> map(IngestionDataset.SCHOOLS, SCHOOLS_HEADER, replace(SCHOOL, column, value)), reason);
        }
    }

    @Nested
    @DisplayName("school_grades.csv")
    class Grades {

        @Test
        @DisplayName("A 2026 grade keeps its letter, components and pct_total_points; components that do not apply are NULL")
        void mapsAGrade() throws IOException {
            var values = map(IngestionDataset.SCHOOL_GRADES, GRADES_HEADER, GRADE_2026);

            assertEquals("A", values.get("letter_grade"));
            assertEquals("A", values.get("fldoe_grade"));
            assertEquals("2026", values.get("school_year"));
            assertEquals("Combination", values.get("fldoe_school_type"));
            assertEquals("69", values.get("pct_total_points"));
            assertEquals("71", values.get("grade3_ela_achievement"));
            assertNull(values.get("graduation_rate"), "does not apply to a K-8: NULL, not 0");
            assertEquals("false", values.get("is_baseline"));
        }

        @ParameterizedTest(name = "FLDOE code {0}")
        @CsvSource({ "I", "N", "P" })
        @DisplayName("I (incomplete), N and P give no letter grade: letter_grade NULL, the FLDOE code kept")
        void ungradedCodesHaveNoLetter(String code) throws IOException {
            String row = "13-0050,13,50,SMALL SCHOOL,2026," + code + ",false,Elementary,false,false,No,"
                + ",,,,,,,,,,,,,,,,";
            var values = map(IngestionDataset.SCHOOL_GRADES, GRADES_HEADER, row);

            assertNull(values.get("letter_grade"), "never a low grade");
            assertEquals(code, values.get("fldoe_grade"));
            assertNull(values.get("pct_total_points"), "unknown, not 0");
        }

        @Test
        @DisplayName("An old year has only its letter: every component and percent is NULL")
        void oldYearHasOnlyTheLetter() throws IOException {
            String row = "13-0041,13,41,CORAL WAY ELEMENTARY,2003,B,false,,,,,,,,,,,,,,,,,,,,,";
            var values = map(IngestionDataset.SCHOOL_GRADES, GRADES_HEADER, row);

            assertEquals("B", values.get("letter_grade"));
            assertNull(values.get("fldoe_school_type"));
            assertNull(values.get("is_charter"));
            for (String component : ReferenceDataRows.GRADE_COMPONENTS) assertNull(values.get(component), component);
            assertNull(values.get("total_points_earned"));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
            "letter that FLDOE does not use|5|X",
            "state id not DD-SSSS|0|130041",
            "district number of another school|1|14",
            "school number of another school|2|42",
            "year that is not a year|4|26",
            "baseline flag missing|6|",
            "unknown FLDOE school type|7|K-8",
            "component above 100|12|101",
            "negative points|23|-1",
            "pct_total_points above 100|25|100.5" })
        @DisplayName("A malformed grade value rejects the row")
        void rejectsMalformedValues(String reason, int column, String value) {
            assertThrows(IllegalArgumentException.class,
                () -> map(IngestionDataset.SCHOOL_GRADES, GRADES_HEADER, replace(GRADE_2026, column, value)), reason);
        }
    }

    @Nested
    @DisplayName("school_zones.csv")
    class Zones {
        private static final String ZONE = "12086,Miami-Dade,ELEMENTARY,Coral Way K-8,13-0041,state_school_id,13-0041,"
            + "Miami-Dade County GIS,OFFICIAL,2026-2027,,,\"MULTIPOLYGON(((-80.22 25.75,-80.21 25.75,-80.21 25.76,-80.22 25.75)))\"";

        @Test
        @DisplayName("A zone keeps its school key, source type and polygon")
        void mapsAZone() throws IOException {
            var values = map(IngestionDataset.SCHOOL_ZONES, ZONES_HEADER, ZONE);

            assertEquals("13-0041", values.get("school_key"));
            assertEquals("OFFICIAL", values.get("source_type"));
            assertNull(values.get("method_fr"));
            assertTrue(values.get("geom_wkt").startsWith("MULTIPOLYGON"));
        }

        @Test
        @DisplayName("A zone without a school keeps an empty school key (NULL)")
        void zoneWithoutSchool() throws IOException {
            String row = "12086,Miami-Dade,ELEMENTARY,Shadowlawn Elementary,,none,,Miami-Dade County GIS,OFFICIAL,2026-2027,,,"
                + "\"MULTIPOLYGON(((0 0,1 0,1 1,0 0)))\"";
            var values = map(IngestionDataset.SCHOOL_ZONES, ZONES_HEADER, row);

            assertNull(values.get("school_key"));
            assertEquals("none", values.get("school_match"));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
            "county of another state|0|13121",
            "unknown level|2|PRE_K",
            "unknown source type|8|DISTRICT",
            "unknown match method|5|address",
            "missing polygon|12|",
            "not a polygon|12|POINT(0 0)" })
        @DisplayName("A malformed zone value rejects the row")
        void rejectsMalformedValues(String reason, int column, String value) throws IOException {
            String row = String.join(",", replaceLast(ZONE, column, value));
            assertThrows(IllegalArgumentException.class, () -> map(IngestionDataset.SCHOOL_ZONES, ZONES_HEADER, row), reason);
        }

        /** The zone row with one field replaced; the quoted polygon is the last field. */
        private List<String> replaceLast(String row, int index, String value) throws IOException {
            CSVRecord record = record(ZONES_HEADER, row);
            List<String> fields = new ArrayList<>();
            for (String field : record) fields.add(field.contains(",") ? "\"" + field + "\"" : field);
            String text = value == null ? "" : value;
            fields.set(index, text.contains(",") ? "\"" + text + "\"" : text);
            return fields;
        }
    }

    @Nested
    @DisplayName("SQL")
    class Sql {

        @ParameterizedTest
        @EnumSource(value = IngestionDataset.class, names = { "SCHOOLS", "SCHOOL_GRADES", "SCHOOL_ZONES" })
        @DisplayName("School files are copied into staging and replace only the counties or years they contain")
        void replaceWhatTheFileContains(IngestionDataset dataset) {
            RowMapper mapper = ReferenceDataRows.forDataset(dataset, "12", List.of(), new ObjectMapper());
            String staging = ReferenceDataSql.stagingTable(dataset);

            assertTrue(ReferenceDataSql.createStaging(dataset, mapper.columns()).startsWith("CREATE TEMP TABLE " + staging));
            String delete = ReferenceDataSql.deleteSchoolScope(dataset);
            assertTrue(delete.startsWith("DELETE FROM " + ReferenceDataSql.targetTable(dataset)));
            assertTrue(delete.contains("FROM " + staging + ")") || delete.contains("FROM " + staging + "\n")
                || delete.contains(" FROM " + staging), "the scope comes from the staging table");
            assertFalse(delete.contains("?"), "no bind parameter");
            assertTrue(ReferenceDataSql.insertTakesSourceFile(dataset));
            assertTrue(ReferenceDataSql.insert(dataset).contains("CAST(? AS varchar)"), "the source file is recorded");
            assertFalse(ReferenceDataSql.loaded(dataset).contains("?"));
            assertEquals(0, ReferenceDataSql.scopeParameters(dataset, "12").length);
            assertFalse(ReferenceDataSql.source(dataset).isBlank());
            assertTrue(ReferenceDataSql.source(dataset).length() <= 300, "fits reference_dataset_loads.source");
            assertTrue(dataset.isSchoolData() && dataset.isReferenceData());
        }

        @Test
        @DisplayName("Schools are upserted on school_key (stable ids) and re-link the zones; only dropped schools are deleted")
        void schoolsAreUpserted() {
            assertTrue(ReferenceDataSql.insert(IngestionDataset.SCHOOLS).contains("ON CONFLICT (school_key) DO UPDATE SET"));
            assertTrue(ReferenceDataSql.insert(IngestionDataset.SCHOOLS)
                .contains("ST_SetSRID(ST_MakePoint(s.longitude, s.latitude), 4326)"));
            assertTrue(ReferenceDataSql.deleteSchoolScope(IngestionDataset.SCHOOLS)
                .contains("NOT EXISTS (SELECT 1 FROM stage_schools s WHERE s.school_key = t.school_key)"));
            assertTrue(ReferenceDataSql.afterSchoolInsert(IngestionDataset.SCHOOLS)
                .contains("UPDATE school_attendance_zones z SET school_id = s.id"));
            assertTrue(ReferenceDataSql.duplicateKey(IngestionDataset.SCHOOLS).contains("GROUP BY school_key"));
            assertTrue(ReferenceDataSql.duplicateStateSchoolId().contains("state_school_id IS NOT NULL"));
        }

        @Test
        @DisplayName("Grades are ranked within the same FLDOE school type and year, only with pct_total_points and a letter")
        void gradesAreRanked() {
            String sql = ReferenceDataSql.afterSchoolInsert(IngestionDataset.SCHOOL_GRADES);

            assertTrue(sql.contains("percent_rank() OVER (PARTITION BY school_year, fldoe_school_type"));
            assertTrue(sql.contains("ORDER BY pct_total_points"));
            assertTrue(sql.contains("pct_total_points IS NOT NULL AND fldoe_school_type IS NOT NULL AND letter_grade IS NOT NULL"));
            assertTrue(sql.contains("count(*) OVER (PARTITION BY school_year, fldoe_school_type) > 1"), "a school alone has no rank");
            assertTrue(sql.contains("school_year IN (SELECT DISTINCT school_year FROM stage_school_grades)"), "only the loaded years");
            assertTrue(ReferenceDataSql.duplicateKey(IngestionDataset.SCHOOL_GRADES)
                .contains("GROUP BY (state_school_id || ' ' || school_year)"));
        }

        @Test
        @DisplayName("Zone polygons are repaired and linked to their school by school_key; invalid ones fail the load")
        void zonesAreRepaired() {
            String sql = ReferenceDataSql.insert(IngestionDataset.SCHOOL_ZONES);

            assertTrue(sql.contains("ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_GeomFromText(s.geom_wkt, 4326)), 3))"));
            assertTrue(sql.contains("LEFT JOIN schools sc ON sc.school_key = s.school_key"));
            assertTrue(ReferenceDataSql.invalidGeometry(IngestionDataset.SCHOOL_ZONES).contains("NOT ST_IsValid(geom)"));
            assertNull(ReferenceDataSql.afterSchoolInsert(IngestionDataset.SCHOOL_ZONES));
            assertNull(ReferenceDataSql.duplicateKey(IngestionDataset.SCHOOL_ZONES), "a school can have several zone rows");
            assertThrows(IllegalArgumentException.class, () -> ReferenceDataSql.deleteSchoolScope(IngestionDataset.FLOOD_ZONES));
        }
    }
}
