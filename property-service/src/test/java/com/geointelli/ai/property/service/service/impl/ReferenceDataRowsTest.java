package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.impl.ReferenceDataRows.RowMapper;

/** Normalisation of the reference rows (SCRUM-201): unknown source values are null, never a default. */
class ReferenceDataRowsTest {
    private static final String FLOOD_HEADER = "fips5,DFIRM_ID,FLD_AR_ID,FLD_ZONE,ZONE_SUBTY,SFHA_TF,STATIC_BFE,DEPTH,"
        + "V_DATUM,LEN_UNIT,VELOCITY,VEL_UNIT,DUAL_ZONE,STUDY_TYP,VERSION_ID,SOURCE_CIT,geom_wkt";

    private static CSVRecord record(String header, String row) throws IOException {
        CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
            .parse(new StringReader(header + "\n" + row + "\n"));
        return parser.iterator().next();
    }

    private static List<String> map(IngestionDataset dataset, String scope, String header, String row) throws IOException {
        RowMapper mapper = ReferenceDataRows.forDataset(dataset, scope, Arrays.asList(header.split(",")), new ObjectMapper());
        List<String> values = mapper.map(record(header, row));
        assertEquals(mapper.columns().size(), values.size(), "one value per staging column");
        return values;
    }

    private static String value(IngestionDataset dataset, String scope, String header, String row, String column)
            throws IOException {
        RowMapper mapper = ReferenceDataRows.forDataset(dataset, scope, Arrays.asList(header.split(",")), new ObjectMapper());
        List<String> values = mapper.map(record(header, row));
        List<String> names = new ArrayList<>();
        mapper.columns().forEach(c -> names.add(c.name()));
        return values.get(names.indexOf(column));
    }

    @Nested
    @DisplayName("FEMA flood zones")
    class Flood {
        private static final String ROW = "12086,12086C,A1,AE,,T,7,-9999,NGVD29,Feet,-99999,,,NP,1.1.1.0,S1,\"POLYGON((0 0,1 0,1 1,0 0))\"";

        @Test
        @DisplayName("-9999 and -99999 are NULL, the flag is a boolean, empty text is NULL and the source row is kept")
        void sentinelsAreNull() throws IOException {
            List<String> values = map(IngestionDataset.FLOOD_ZONES, "12086", FLOOD_HEADER, ROW);
            assertEquals("1", values.get(0), "source_row");
            assertEquals("true", value(IngestionDataset.FLOOD_ZONES, "12086", FLOOD_HEADER, ROW, "sfha"));
            assertEquals("7", value(IngestionDataset.FLOOD_ZONES, "12086", FLOOD_HEADER, ROW, "static_bfe"));
            assertNull(value(IngestionDataset.FLOOD_ZONES, "12086", FLOOD_HEADER, ROW, "depth"));
            assertNull(value(IngestionDataset.FLOOD_ZONES, "12086", FLOOD_HEADER, ROW, "velocity"));
            assertNull(value(IngestionDataset.FLOOD_ZONES, "12086", FLOOD_HEADER, ROW, "zone_subty"));
            assertTrue(value(IngestionDataset.FLOOD_ZONES, "12086", FLOOD_HEADER, ROW, "geom_wkt").startsWith("POLYGON"));
        }

        @Test
        @DisplayName("A row of another county, a bad flag, a bad number or a non-polygon WKT is rejected with the column")
        void invalidRowsRejected() {
            var county = assertThrows(IllegalArgumentException.class,
                () -> map(IngestionDataset.FLOOD_ZONES, "12087", FLOOD_HEADER, ROW));
            assertTrue(county.getMessage().contains("12086"));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> map(IngestionDataset.FLOOD_ZONES, "12086",
                FLOOD_HEADER, ROW.replace(",T,7,", ",Y,7,"))).getMessage().contains("SFHA_TF"));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> map(IngestionDataset.FLOOD_ZONES, "12086",
                FLOOD_HEADER, ROW.replace(",T,7,", ",T,seven,"))).getMessage().contains("STATIC_BFE"));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> map(IngestionDataset.FLOOD_ZONES, "12086",
                FLOOD_HEADER, ROW.replace("\"POLYGON((0 0,1 0,1 1,0 0))\"", "POINT(0 0)"))).getMessage().contains("geom_wkt"));
        }

        @Test
        @DisplayName("An empty geometry is kept as NULL so the source row is not lost")
        void emptyGeometryKept() throws IOException {
            String row = ROW.replace("\"POLYGON((0 0,1 0,1 1,0 0))\"", "");
            assertNull(value(IngestionDataset.FLOOD_ZONES, "12086", FLOOD_HEADER, row, "geom_wkt"));
        }
    }

    @Nested
    @DisplayName("FEMA National Risk Index")
    class Nri {
        private static final String HEADER = "TRACTFIPS,NRI_VER,RISK_SCORE,RISK_RATNG,HRCN_RISKS,HRCN_RISKR,IFLD_RISKS,"
            + "IFLD_RISKR,CFLD_RISKS,CFLD_RISKR,TRND_RISKS,TRND_RISKR,WFIR_RISKS,WFIR_RISKR,SWND_RISKS,SWND_RISKR,"
            + "LTNG_RISKS,LTNG_RISKR,HWAV_RISKS,HWAV_RISKR";
        private static final String ROW = "12086000200,December 2025,12.3,Relatively Low,,Insufficient Data,0.000000,"
            + "No Rating,,Not Applicable,20,Relatively Low,5,Very Low,6,Very Low,7,Very Low,8,Very High";

        @Test
        @DisplayName("Insufficient Data, No Rating (with its 0 score) and Not Applicable are NULL ratings and scores")
        void noEvidenceIsNull() throws IOException {
            assertNull(value(IngestionDataset.NRI_TRACTS, "12", HEADER, ROW, "hrcn_rating"));
            assertNull(value(IngestionDataset.NRI_TRACTS, "12", HEADER, ROW, "ifld_rating"));
            assertNull(value(IngestionDataset.NRI_TRACTS, "12", HEADER, ROW, "ifld_score"), "0 next to No Rating is no evidence");
            assertNull(value(IngestionDataset.NRI_TRACTS, "12", HEADER, ROW, "cfld_score"));
            assertEquals("Relatively Low", value(IngestionDataset.NRI_TRACTS, "12", HEADER, ROW, "risk_rating"));
            assertEquals("12.3", value(IngestionDataset.NRI_TRACTS, "12", HEADER, ROW, "risk_score"));
            assertEquals("12086", value(IngestionDataset.NRI_TRACTS, "12", HEADER, ROW, "fips5"));
            String raw = value(IngestionDataset.NRI_TRACTS, "12", HEADER, ROW, "raw");
            assertTrue(raw.contains("\"HRCN_RISKR\":\"Insufficient Data\""), "raw keeps the source text");
        }

        @Test
        @DisplayName("A tract of another state is rejected")
        void otherStateRejected() {
            assertThrows(IllegalArgumentException.class, () -> map(IngestionDataset.NRI_TRACTS, "13", HEADER, ROW));
        }
    }

    @Nested
    @DisplayName("NOAA storm events")
    class Storm {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({ "25.00K,25000", "1.5M,1500000", "2B,2000000000", ".5K,500", "0,0", "0.00K,0", "10,10", "3k,3000" })
        @DisplayName("Damage text is converted to US dollars")
        void damage(String text, String dollars) {
            assertEquals(dollars, ReferenceDataRows.damage(text, "DAMAGE_PROPERTY"));
        }

        @Test
        @DisplayName("Missing damage is NULL (not reported) and malformed damage is rejected")
        void damageMissingOrMalformed() {
            assertNull(ReferenceDataRows.damage("", "DAMAGE_PROPERTY"));
            assertNull(ReferenceDataRows.damage(null, "DAMAGE_PROPERTY"));
            assertNull(ReferenceDataRows.damage("K", "DAMAGE_PROPERTY"), "a unit without an amount is unknown, not 0");
            assertThrows(IllegalArgumentException.class, () -> ReferenceDataRows.damage("1.5X", "DAMAGE_PROPERTY"));
        }

        @Test
        @DisplayName("Times come from year-month, day and HHmm with the zone offset; an unknown zone is NULL, not guessed")
        void times() {
            assertEquals("2024-09-10T09:30:00-05:00", ReferenceDataRows.noaaTime("202409", "10", "930", "EST-5", "BEGIN"));
            assertEquals("1955-06-03T00:00:00-06:00", ReferenceDataRows.noaaTime("195506", "3", "0", "CST", "BEGIN"));
            assertEquals("2024-09-11T00:00:00-04:00", ReferenceDataRows.noaaTime("202409", "10", "2400", "EDT-4", "END"));
            assertEquals("2024-01-05T12:00:00-06:00", ReferenceDataRows.noaaTime("202401", "5", "1200", "CSt", "BEGIN"));
            assertNull(ReferenceDataRows.noaaTime("202409", "10", "930", "XYZ", "BEGIN"));
            assertNull(ReferenceDataRows.noaaTime("", "", "", "EST", "BEGIN"));
            assertThrows(IllegalArgumentException.class, () -> ReferenceDataRows.noaaTime("202413", "10", "930", "EST", "BEGIN"));
            assertThrows(IllegalArgumentException.class, () -> ReferenceDataRows.noaaTime("202409", "", "930", "EST", "BEGIN"));
        }

        @Test
        @DisplayName("A zone row has no county and no point; a point needs both coordinates")
        void zoneRow() throws IOException {
            String header = "fips5,BEGIN_YEARMONTH,BEGIN_DAY,BEGIN_TIME,END_YEARMONTH,END_DAY,END_TIME,EPISODE_ID,EVENT_ID,"
                + "STATE_FIPS,EVENT_TYPE,CZ_TYPE,CZ_FIPS,CZ_NAME,CZ_TIMEZONE,INJURIES_DIRECT,INJURIES_INDIRECT,DEATHS_DIRECT,"
                + "DEATHS_INDIRECT,DAMAGE_PROPERTY,DAMAGE_CROPS,MAGNITUDE,MAGNITUDE_TYPE,FLOOD_CAUSE,TOR_F_SCALE,BEGIN_LAT,BEGIN_LON";
            String row = ",202409,10,0,202409,10,100,,77,12,High Surf,Z,173,COAST,EST-5,0,0,0,0,,,,,,,25.7,";
            assertNull(value(IngestionDataset.STORM_EVENTS, "12", header, row, "fips5"));
            assertNull(value(IngestionDataset.STORM_EVENTS, "12", header, row, "begin_lat"), "a lone latitude is not a point");
            assertNull(value(IngestionDataset.STORM_EVENTS, "12", header, row, "episode_id"));
            assertEquals("12", value(IngestionDataset.STORM_EVENTS, "12", header, row, "state_fips"));
            assertThrows(IllegalArgumentException.class, () -> map(IngestionDataset.STORM_EVENTS, "13", header, row));
            assertThrows(IllegalArgumentException.class,
                () -> map(IngestionDataset.STORM_EVENTS, "12", header, row.replace(",Z,", ",Q,")));
        }
    }

    @Test
    @DisplayName("COPY lines: NULL is an empty unquoted field; commas and quotes are quoted and escaped")
    void copyLines() throws IOException {
        StringWriter out = new StringWriter();
        ReferenceDataImportServiceImpl.writeLine(out, Arrays.asList("1", null, "POLYGON((0 0,1 0))", "say \"hi\"", ""));
        assertEquals("1,,\"POLYGON((0 0,1 0))\",\"say \"\"hi\"\"\",\"\"\n", out.toString());
    }
}
