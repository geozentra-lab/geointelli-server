package com.geointelli.ai.property.service.backfill;

import com.geointelli.ai.property.service.integration.mapillary.MapillaryCandidateMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapillaryPreviewTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void handlesCompassWraparound() {
        assertEquals(2, MapillaryCandidateMatcher.angleDifference(359, 1), 0.001);
        assertEquals(180, MapillaryCandidateMatcher.angleDifference(0, 180), 0.001);
        assertEquals(0, MapillaryCandidateMatcher.angleDifference(360, 0), 0.001);
    }

    @Test void directionOutweighsProximityAndScoreIsNormalized() {
        assertTrue(MapillaryCandidateMatcher.score(40, 0, 80, 35)
                > MapillaryCandidateMatcher.score(1, 30, 80, 35));
        assertEquals(100, MapillaryCandidateMatcher.score(0, 0, 80, 35), 0.0001);
        assertEquals(75, MapillaryCandidateMatcher.score(80, 0, 80, 35), 0.0001);
        assertEquals(25, MapillaryCandidateMatcher.score(0, 35, 80, 35), 0.0001);
    }

    @Test void missingComputedMetadataIsRejectedWithoutFallback() throws Exception {
        var image = json.readTree("""
            {"id":"1","geometry":{"coordinates":[-80,25]},"compass_angle":0,"is_pano":true,"thumb_1024_url":"https://example.com/a"}
            """);
        assertEquals("missing-or-invalid-computed-geometry", MapillaryCandidateMatcher.missingMetadata(image));
        var object = (com.fasterxml.jackson.databind.node.ObjectNode) image;
        object.set("computed_geometry", json.readTree("{\"coordinates\":[-80,25]}"));
        assertEquals("missing-or-invalid-computed-heading", MapillaryCandidateMatcher.missingMetadata(image));
        object.put("computed_compass_angle", 359);
        assertNull(MapillaryCandidateMatcher.missingMetadata(image));
    }

    @Test void validatesOptionsAndKeepsFolioFoldersInsideOutput() {
        var options = MapillaryPreview.Options.parse(new String[]{});
        assertEquals(20, options.limit()); assertEquals(80, options.radius()); assertEquals(35, options.maxAngle());
        assertThrows(IllegalArgumentException.class, () -> MapillaryPreview.Options.parse(new String[]{"--max-angle=NaN"}));
        assertThrows(IllegalArgumentException.class, () -> MapillaryPreview.Options.parse(new String[]{"--max-distance=81"}));
        assertThrows(IllegalArgumentException.class, () -> MapillaryPreview.Options.parse(new String[]{"--candidates=2"}));
        assertEquals("property-1", MapillaryPreview.folderName(null, 1));
        assertFalse(MapillaryPreview.folderName("../../123", 1).contains("/"));
        var nearby = MapillaryPreview.Options.parse(new String[]{"--near-property=1", "--exclude-properties=1,2", "--panoramas-only=true"});
        assertEquals(1L, nearby.nearProperty()); assertEquals(List.of(1L,2L), nearby.excludedProperties()); assertTrue(nearby.panoramasOnly());
        assertThrows(IllegalArgumentException.class, () -> MapillaryPreview.Options.parse(new String[]{"--near-property=0"}));
    }

    @Test void paginationExtractsOnlyCursorAndRetriesAreBounded() throws Exception {
        assertEquals("abc+123", MapillaryPreview.nextCursor(json.readTree("""
            {"next":"https://graph.mapillary.com/images?access_token=SECRET&after=abc%2B123"}
            """)));
        assertNull(MapillaryPreview.nextCursor(json.readTree("{\"next\":\"bad%url\"}")));
        assertEquals(60, MapillaryPreview.retryDelay("300", 0));
        assertEquals(10, MapillaryPreview.retryDelay("invalid", 1));
    }
}

@EnabledIfEnvironmentVariable(named = "MAPILLARY_POSTGIS_TESTS", matches = "1")
class MapillaryPostgisTest {
    @Test void projectedPanoramaCanFaceParcelIndependentOfReferenceHeading() throws Exception {
        var images = new ObjectMapper().readTree("""
            [{"id":"1","computed_geometry":{"coordinates":[-80.4,25.6998]},"computed_compass_angle":180,"is_pano":true,"thumb_1024_url":"https://example.com/a"}]
            """);
        String polygon="POLYGON((-80.4001 25.7000,-80.3999 25.7000,-80.3999 25.7002,-80.4001 25.7002,-80.4001 25.7000))";
        try(var connection=MapillaryPreview.openConnection()) {
            assertTrue(MapillaryCandidateMatcher.match(connection,polygon,images,80,35).candidates().isEmpty());
            var result=MapillaryCandidateMatcher.match(connection,polygon,images,80,35,true);
            assertEquals(1,result.candidates().size());assertEquals(0,result.candidates().get(0).angle());
            assertEquals(result.candidates().get(0).bearing(),result.candidates().get(0).heading());
        }
    }
    @Test void measuresAgainstPolygonRejectsAwayPanoramasAndRanksByDirection() throws Exception {
        var images = new ObjectMapper().readTree("""
            [
              {"id":"1","computed_geometry":{"coordinates":[-80.4,25.69999]},"computed_compass_angle":30,"thumb_1024_url":"https://example.com/1"},
              {"id":"2","computed_geometry":{"coordinates":[-80.4,25.6998]},"computed_compass_angle":0,"thumb_1024_url":"https://example.com/2"},
              {"id":"3","computed_geometry":{"coordinates":[-80.4,25.7004]},"computed_compass_angle":0,"is_pano":true,"thumb_1024_url":"https://example.com/3"},
              {"id":"4","computed_geometry":{"coordinates":[-80.4,25.697]},"computed_compass_angle":0,"thumb_1024_url":"https://example.com/4"},
              {"id":"5","computed_geometry":{"coordinates":[-80.4,25.7001]},"computed_compass_angle":0,"thumb_1024_url":"https://example.com/5"},
              {"id":"2","computed_geometry":{"coordinates":[-80.4,25.6998]},"computed_compass_angle":0,"thumb_1024_url":"https://example.com/2"},
              {"id":"6","computed_geometry":{"coordinates":[-80.4,25.6998]},"thumb_1024_url":"https://example.com/6"}
            ]
            """);
        try (var connection = MapillaryPreview.openConnection()) {
            var result = MapillaryCandidateMatcher.match(connection,
                    "POLYGON((-80.4001 25.7000,-80.3999 25.7000,-80.3999 25.7002,-80.4001 25.7002,-80.4001 25.7000))",
                    images, 80, 35);
            assertEquals(2, result.candidates().size());
            assertEquals("2", result.candidates().get(0).image().path("id").asText());
            assertEquals(0, result.candidates().get(0).bearing(), 0.001);
            assertEquals(22.15, result.candidates().get(0).distance(), 0.2);
            assertEquals(25.7000, result.candidates().get(0).targetLatitude(), 0.000001);
            assertEquals(1, result.rejected().get("facing-away"));
            assertEquals(1, result.rejected().get("beyond-distance-threshold"));
            assertEquals(1, result.rejected().get("undefined-bearing-camera-on-parcel"));
            assertEquals(1, result.rejected().get("duplicate-image-id"));
            assertEquals(1, result.rejected().get("missing-or-invalid-computed-heading"));
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SHOW default_transaction_read_only")) {
                assertTrue(rows.next()); assertEquals("on", rows.getString(1));
            }
        }
    }
}
