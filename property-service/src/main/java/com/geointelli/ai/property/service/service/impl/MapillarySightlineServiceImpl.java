package com.geointelli.ai.property.service.service.impl;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.service.MapillarySightlineService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;

/** Conservatively flags rays entering neighboring parcels; parcel land is not a building footprint. */
@Service
public final class MapillarySightlineServiceImpl implements MapillarySightlineService {
    private final ObjectMapper json = new ObjectMapper();
    private final GeometryFactory factory = new GeometryFactory();

    @Override
    public Map<String, Object> assess(Map<String, Object> review, JsonNode camera, double longitude, double latitude) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Long> blockers = new ArrayList<>();
        result.put("interveningPropertyIds", blockers);
        result.put("sightlineDecision", "evidence-unavailable");
        if (camera.size() < 2 || review == null || !(review.get("neighbors") instanceof List<?> neighbors)) return result;
        double lon = camera.get(0).asDouble(), lat = camera.get(1).asDouble();
        Geometry ray = factory.createLineString(new Coordinate[]{new Coordinate(0, 0), project(longitude, latitude, lon, lat)});
        try {
            for (Object item : neighbors) {
                Map<?, ?> neighbor = (Map<?, ?>) item;
                if (neighbor.get("geometryGeoJson") == null) continue;
                JsonNode geometry = json.readTree(neighbor.get("geometryGeoJson").toString());
                Geometry polygon = geometry(geometry, lon, lat);
                // A small inset ignores shared boundary touches and minor coordinate rounding.
                Geometry interior = polygon.buffer(-0.5);
                if (!interior.isEmpty() && ray.intersection(interior).getLength() > 1.0) {
                    blockers.add(((Number)neighbor.get("propertyId")).longValue());
                }
            }
            result.put("sightlineDecision", blockers.isEmpty() ? "no-intervening-parcel-detected" : "possible-intervening-parcel");
        } catch (Exception error) {
            result.put("sightlineDecision", blockers.isEmpty() ? "evidence-unavailable" : "possible-intervening-parcel");
            result.put("sightlineErrorType", error.getClass().getSimpleName());
        }
        return result;
    }

    /** Converts the bounded neighborhood to a local meter approximation around the camera. */
    private Coordinate project(double lon, double lat, double originLon, double originLat) {
        return new Coordinate(Math.toRadians(lon - originLon) * 6_371_008.8 * Math.cos(Math.toRadians(originLat)),
            Math.toRadians(lat - originLat) * 6_371_008.8);
    }

    private Geometry geometry(JsonNode node, double lon, double lat) {
        if (node.path("type").asText().equals("Polygon")) return polygon(node.path("coordinates"), lon, lat);
        if (node.path("type").asText().equals("MultiPolygon")) {
            var polygons = new org.locationtech.jts.geom.Polygon[node.path("coordinates").size()];
            for (int i = 0; i < polygons.length; i++) polygons[i] = polygon(node.path("coordinates").get(i), lon, lat);
            return factory.createMultiPolygon(polygons);
        }
        throw new IllegalArgumentException("Unsupported parcel geometry");
    }

    private org.locationtech.jts.geom.Polygon polygon(JsonNode rings, double lon, double lat) {
        LinearRing shell = ring(rings.get(0), lon, lat);
        LinearRing[] holes = new LinearRing[Math.max(0, rings.size() - 1)];
        for (int i = 0; i < holes.length; i++) holes[i] = ring(rings.get(i + 1), lon, lat);
        return factory.createPolygon(shell, holes);
    }

    private LinearRing ring(JsonNode points, double lon, double lat) {
        Coordinate[] coordinates = new Coordinate[points.size()];
        for (int i = 0; i < coordinates.length; i++) coordinates[i] = project(points.get(i).get(0).asDouble(), points.get(i).get(1).asDouble(), lon, lat);
        return factory.createLinearRing(coordinates);
    }
}
