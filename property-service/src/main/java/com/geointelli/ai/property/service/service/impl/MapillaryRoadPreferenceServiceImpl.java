package com.geointelli.ai.property.service.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.service.MapillaryRoadPreferenceService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Uses local centerlines and address ranges, with explicit missing/ambiguous-data fallback. */
@Service
public final class MapillaryRoadPreferenceServiceImpl implements MapillaryRoadPreferenceService {
    private static final Pattern ADDRESS = Pattern.compile(
        "^(\\d+)\\s+(NW|NE|SW|SE|N|S|E|W)\\s+(\\S+)\\s+(ST|CT|AVE|PL|RD|DR|TER|LN|WAY|BLVD)\\b");
    private final List<JsonNode> roads = new ArrayList<>();
    private final String sourceStatus;

    public MapillaryRoadPreferenceServiceImpl() {
        this(System.getProperty("mapillary.roads.file",
            "mapillary-validation/road-data-check/miami-dade-street-centerlines-sample.geojson"));
    }

    @Autowired
    public MapillaryRoadPreferenceServiceImpl(@Value("${mapillary.roads-file:}") String filename) {
        Path source = Path.of(filename.isBlank()
            ? "mapillary-validation/road-data-check/miami-dade-street-centerlines-sample.geojson" : filename);
        String status = "available-local-sample";
        try {
            if (!Files.isRegularFile(source)) status = "road-sample-unavailable";
            else {
                JsonNode data = new ObjectMapper().readTree(source.toFile());
                if (!data.path("features").isArray()) status = "invalid-road-sample";
                else for (JsonNode road : data.path("features")) {
                    String type = road.path("geometry").path("type").asText();
                    if (type.equals("LineString") || type.equals("MultiLineString")) roads.add(road);
                }
            }
        } catch (Exception error) { status = "road-sample-unreadable"; }
        sourceStatus = status;
    }

    @Override
    public Map<String, Object> assess(String address, JsonNode camera, double targetLongitude, double targetLatitude) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roadDataStatus", sourceStatus); result.put("roadPreference", "evidence-unavailable");
        result.put("roadPreferencePriority", 1);
        if (address == null || roads.isEmpty() || camera.size() < 2) return result;
        var matcher = ADDRESS.matcher(address.trim().toUpperCase(Locale.ROOT));
        if (!matcher.find()) { result.put("roadPreference", "unsupported-address-format"); return result; }
        int houseNumber;
        try { houseNumber = Integer.parseInt(matcher.group(1)); }
        catch (NumberFormatException error) { result.put("roadPreference", "unsupported-house-number"); return result; }
        String street = matcher.group(2) + " " + normalize(matcher.group(3)) + " " + matcher.group(4);
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode road : roads) {
            JsonNode fields = road.path("properties");
            if (street.equals(street(fields)) && (inRange(houseNumber, fields, "L") || inRange(houseNumber, fields, "R"))
                    && distance(road.path("geometry"), targetLongitude, targetLatitude) <= 80) matches.add(road);
        }
        if (matches.size() != 1) {
            result.put("roadPreference", matches.isEmpty() ? "no-address-range-match-in-sample" : "ambiguous-address-range-match");
            return result;
        }
        result.put("matchedAddressStreet", street);
        result.put("addressRoadObjectId", matches.get(0).path("properties").path("OBJECTID").asLong());
        double addressDistance = Double.POSITIVE_INFINITY, otherDistance = Double.POSITIVE_INFINITY;
        String nearestOther = null;
        for (JsonNode road : roads) {
            double meters = distance(road.path("geometry"), camera.get(0).asDouble(), camera.get(1).asDouble());
            String name = street(road.path("properties"));
            if (street.equals(name)) addressDistance = Math.min(addressDistance, meters);
            else if (meters < otherDistance) { otherDistance = meters; nearestOther = name; }
        }
        result.put("cameraAddressStreetDistanceMeters", Double.isFinite(addressDistance) ? addressDistance : null);
        result.put("cameraOtherStreetDistanceMeters", Double.isFinite(otherDistance) ? otherDistance : null);
        result.put("nearestOtherStreet", nearestOther);
        String decision;
        if (addressDistance <= 20 && addressDistance + 3 < otherDistance) decision = "address-street";
        else if (otherDistance <= 20 && otherDistance + 3 < addressDistance) decision = "other-street";
        else decision = "intersection-or-uncertain-camera-street";
        result.put("roadPreference", decision);
        result.put("roadPreferencePriority", decision.equals("address-street") ? 2 : decision.equals("other-street") ? 0 : 1);
        result.put("frontFacadeMeaning", "address-street preference only; entrance and facade identity unverified");
        return result;
    }

    private String normalize(String value) { return value.replaceAll("(?<=\\d)(ST|ND|RD|TH)$", ""); }

    private String street(JsonNode fields) {
        return fields.path("PRE_DIR").asText("").toUpperCase(Locale.ROOT) + " "
            + normalize(fields.path("ST_NAME").asText("").toUpperCase(Locale.ROOT)) + " "
            + fields.path("ST_TYPE").asText("").toUpperCase(Locale.ROOT);
    }

    private boolean inRange(int number, JsonNode fields, String side) {
        int first = fields.path(side + "_ADD_FROM").asInt(), last = fields.path(side + "_ADD_TO").asInt();
        return first > 0 && last > 0 && number >= Math.min(first, last) && number <= Math.max(first, last)
            && (first % 2 != last % 2 || number % 2 == first % 2);
    }

    /** Measures short local distances in meters; original WGS84 geometry remains unchanged. */
    private double distance(JsonNode geometry, double lon, double lat) {
        JsonNode coordinates = geometry.path("coordinates");
        if (geometry.path("type").asText().equals("LineString")) return lineDistance(coordinates, lon, lat);
        double best = Double.POSITIVE_INFINITY;
        for (JsonNode line : coordinates) best = Math.min(best, lineDistance(line, lon, lat));
        return best;
    }

    private double lineDistance(JsonNode points, double lon, double lat) {
        double best = Double.POSITIVE_INFINITY;
        double xScale = Math.PI / 180 * 6_371_008.8 * Math.cos(Math.toRadians(lat));
        double yScale = Math.PI / 180 * 6_371_008.8;
        for (int i = 1; i < points.size(); i++) {
            double ax = (points.get(i - 1).get(0).asDouble() - lon) * xScale;
            double ay = (points.get(i - 1).get(1).asDouble() - lat) * yScale;
            double bx = (points.get(i).get(0).asDouble() - lon) * xScale;
            double by = (points.get(i).get(1).asDouble() - lat) * yScale;
            double dx = bx - ax, dy = by - ay, length = dx * dx + dy * dy;
            double t = length == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / length));
            best = Math.min(best, Math.hypot(ax + t * dx, ay + t * dy));
        }
        return best;
    }
}
