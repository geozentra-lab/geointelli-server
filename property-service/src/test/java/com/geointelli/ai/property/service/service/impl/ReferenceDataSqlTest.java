package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.impl.ReferenceDataRows.RowMapper;

/** SQL building of the COPY loads (SCRUM-201); the PostGIS behaviour is checked by ReferenceDataImportDatabaseTest. */
class ReferenceDataSqlTest {

    private static RowMapper mapper(IngestionDataset dataset) {
        return ReferenceDataRows.forDataset(dataset, dataset == IngestionDataset.FLOOD_ZONES ? "12086" : "12",
            List.of("TRACTFIPS"), new ObjectMapper());
    }

    @ParameterizedTest
    @EnumSource(value = IngestionDataset.class, names = { "CENSUS_TRACTS", "NRI_TRACTS", "FLOOD_ZONES", "STORM_EVENTS" })
    @DisplayName("Every reference dataset copies CSV with a header into a temporary staging table and replaces one scope")
    void copyIntoStaging(IngestionDataset dataset) {
        RowMapper mapper = mapper(dataset);
        String staging = ReferenceDataSql.stagingTable(dataset);

        assertTrue(ReferenceDataSql.createStaging(dataset, mapper.columns()).matches("CREATE TEMP TABLE " + staging
            + " \\(source_row integer, .*\\) ON COMMIT DROP"));
        assertEquals("COPY " + staging + " (" + String.join(", ", mapper.columns().stream().map(c -> c.name()).toList())
            + ") FROM STDIN WITH (FORMAT csv, HEADER true)", ReferenceDataSql.copy(dataset, mapper.columns()));
        assertTrue(ReferenceDataSql.deleteScope(dataset).startsWith("DELETE FROM " + ReferenceDataSql.targetTable(dataset)));
        assertTrue(ReferenceDataSql.deleteScope(dataset).endsWith("= ?"), "the scope is a bind parameter");
        assertTrue(ReferenceDataSql.insert(dataset).startsWith("INSERT INTO " + ReferenceDataSql.targetTable(dataset)));
        assertTrue(ReferenceDataSql.insert(dataset).contains("FROM " + staging + " s"));
        assertTrue(ReferenceDataSql.loaded(dataset).contains("AS loaded"));
        assertFalse(ReferenceDataSql.source(dataset).isBlank());
    }

    @Test
    @DisplayName("Flood polygons are repaired, reduced to polygons, subdivided to 256 vertices and kept without area")
    void floodInsertRepairsAndSubdivides() {
        String sql = ReferenceDataSql.insert(IngestionDataset.FLOOD_ZONES);

        assertTrue(sql.contains("ST_Subdivide(ST_CollectionExtract(ST_MakeValid(ST_GeomFromText(s.geom_wkt, 4326)), 3), 256)"));
        assertTrue(sql.contains("ST_Multi(d.geom)::geometry(MultiPolygon, 4326)"));
        assertTrue(sql.contains("LEFT JOIN LATERAL"), "a polygon without area keeps its row");
        assertTrue(sql.contains("s.source_row"), "the parts of one polygon can be regrouped");
        assertEquals("DELETE FROM flood_zones WHERE fips5 = ?", ReferenceDataSql.deleteScope(IngestionDataset.FLOOD_ZONES));
    }

    @Test
    @DisplayName("Storm points are built from longitude and latitude only when both exist; keys are checked for duplicates")
    void stormAndKeys() {
        assertTrue(ReferenceDataSql.insert(IngestionDataset.STORM_EVENTS)
            .contains("WHEN s.begin_lat IS NOT NULL AND s.begin_lon IS NOT NULL\n        THEN ST_SetSRID(ST_MakePoint(s.begin_lon, s.begin_lat), 4326)"));
        assertTrue(ReferenceDataSql.duplicateKey(IngestionDataset.STORM_EVENTS).contains("GROUP BY event_id"));
        assertTrue(ReferenceDataSql.duplicateKey(IngestionDataset.NRI_TRACTS).contains("GROUP BY tract_fips"));
        assertNull(ReferenceDataSql.duplicateKey(IngestionDataset.FLOOD_ZONES), "FLD_AR_ID is not unique in FEMA's data");
        assertThrows(IllegalArgumentException.class, () -> ReferenceDataSql.insert(IngestionDataset.ADDRESSES));
    }
}
