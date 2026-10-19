package com.geointelli.ai.property.service.integration.mapillary;

import com.geointelli.ai.property.service.service.MapillaryCandidateSelectionService;
import com.geointelli.ai.property.service.service.impl.MapillaryCandidateSelectionServiceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;

public final class MapillaryCandidateMatcher {
    public record Candidate(JsonNode image, double distance, double bearing, double heading,
                     double angle, double score, double targetLongitude, double targetLatitude) {}
    public record Result(List<Candidate> candidates, Map<String, Integer> rejected) {}
    public static final String SPATIAL_SQL = """
        WITH parcel AS (SELECT ST_GeomFromText(?, 4326) AS geom), cameras AS (
            SELECT c.*, ST_SetSRID(ST_MakePoint(c.lon, c.lat), 4326) AS camera
            FROM jsonb_to_recordset(CAST(? AS jsonb)) AS c(id text, lon float8, lat float8, heading float8)
        ), targets AS (
            SELECT c.*, p.geom, ST_PointOnSurface(component.geom) AS target
            FROM cameras c CROSS JOIN parcel p
            CROSS JOIN LATERAL (
                SELECT d.geom FROM ST_Dump(p.geom) d
                ORDER BY ST_Distance(c.camera::geography, d.geom::geography) LIMIT 1
            ) component
        )
        SELECT id, heading, ST_Distance(camera::geography, geom::geography) AS distance,
               degrees(ST_Azimuth(camera::geography, target::geography)) AS bearing,
               ST_X(target) AS target_lon, ST_Y(target) AS target_lat FROM targets
        """;

    public static Result match(Connection connection, String polygonWkt, JsonNode images,
                        double maxDistance, double maxAngle) throws Exception {
        return match(connection, polygonWkt, images, maxDistance, maxAngle, false);
    }

    public static Result match(Connection connection, String polygonWkt, JsonNode images,
                        double maxDistance, double maxAngle, boolean projectPanoramas) throws Exception {
        List<Map<String, Object>> cameras = new ArrayList<>();
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        Map<String, Integer> rejected = new LinkedHashMap<>();
        MapillaryCandidateSelectionService selection = new MapillaryCandidateSelectionServiceImpl();
        for (JsonNode image : images) {
            if (projectPanoramas && !image.path("is_pano").asBoolean()) { reject(rejected, "not-a-panorama"); continue; }
            String reason = missingMetadata(image);
            if (reason != null) { reject(rejected, reason); continue; }
            if (!selection.isDaylightCapture(image)) {
                reject(rejected, "outside-daylight-window-or-missing-capture-time"); continue;
            }
            String id = image.path("id").asText();
            if (byId.putIfAbsent(id, image) != null) { reject(rejected, "duplicate-image-id"); continue; }
            JsonNode coords = image.path("computed_geometry").path("coordinates");
            cameras.add(Map.of("id", id, "lon", coords.get(0).asDouble(), "lat", coords.get(1).asDouble(),
                    "heading", image.path("computed_compass_angle").asDouble()));
        }
        List<Candidate> candidates = new ArrayList<>();
        if (cameras.isEmpty()) return new Result(candidates, rejected);
        try (PreparedStatement statement = connection.prepareStatement(SPATIAL_SQL)) {
            statement.setString(1, polygonWkt);
            statement.setString(2, new ObjectMapper().writeValueAsString(cameras));
            statement.setQueryTimeout(60);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    double distance = rows.getDouble("distance");
                    Double bearing = (Double) rows.getObject("bearing");
                    double heading = rows.getDouble("heading");
                    if (bearing == null || distance < 0.01) { reject(rejected, "undefined-bearing-camera-on-parcel"); continue; }
                    if (distance > maxDistance) { reject(rejected, "beyond-distance-threshold"); continue; }
                    double angle = angleDifference(heading, bearing);
                    // A full 360-degree source can look in any direction. The rendered view faces the parcel.
                    if (projectPanoramas) { heading = bearing; angle = 0; }
                    if (angle > maxAngle) { reject(rejected, "facing-away"); continue; }
                    candidates.add(new Candidate(byId.get(rows.getString("id")), distance, bearing, heading, angle,
                            score(distance, angle, maxDistance, maxAngle), rows.getDouble("target_lon"), rows.getDouble("target_lat")));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparingDouble(Candidate::angle).thenComparingDouble(Candidate::distance)
                .thenComparing(c -> c.image().path("id").asText()));
        return new Result(candidates, rejected);
    }

    public static String missingMetadata(JsonNode image) {
        if (!image.path("id").asText().matches("[0-9]+")) return "missing-or-invalid-id";
        JsonNode coords = image.path("computed_geometry").path("coordinates");
        if (coords.size() < 2 || !coords.get(0).isNumber() || !coords.get(1).isNumber()
                || !Double.isFinite(coords.get(0).asDouble()) || !Double.isFinite(coords.get(1).asDouble())
                || Math.abs(coords.get(0).asDouble()) > 180 || Math.abs(coords.get(1).asDouble()) > 90) {
            return "missing-or-invalid-computed-geometry";
        }
        JsonNode heading = image.path("computed_compass_angle");
        if (!heading.isNumber() || !Double.isFinite(heading.asDouble()) || heading.asDouble() < 0 || heading.asDouble() > 360) {
            return "missing-or-invalid-computed-heading";
        }
        if (image.path("thumb_1024_url").asText().isBlank()) return "missing-thumbnail";
        return null;
    }
    public static double angleDifference(double heading, double bearing) {
        return Math.abs(((heading - bearing + 540) % 360 + 360) % 360 - 180);
    }
    public static double score(double distance, double angle, double maxDistance, double maxAngle) {
        return 100 * (0.75 * Math.max(0, 1 - angle / maxAngle) + 0.25 * Math.max(0, 1 - distance / maxDistance));
    }
    private static void reject(Map<String, Integer> reasons, String reason) { reasons.merge(reason, 1, Integer::sum); }
}
