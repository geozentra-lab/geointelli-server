package com.geointelli.ai.property.service.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.service.MapillaryCandidateSelectionService;
import com.geointelli.ai.property.service.integration.mapillary.MapillaryCandidateMatcher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Standalone selection service; it needs neither Spring nor database writes. */
@Service
public final class MapillaryCandidateSelectionServiceImpl implements MapillaryCandidateSelectionService {

    @Override
    public boolean isDaylightCapture(JsonNode image) {
        long now = Instant.now().toEpochMilli();
        long captured = image.path("captured_at").asLong(0);
        var coordinates = image.path("computed_geometry").path("coordinates");
        if (captured <= 0 || captured > now || coordinates.size() < 2
                || !coordinates.get(0).isNumber()) return false;
        double longitude = coordinates.get(0).asDouble();
        if (!Double.isFinite(longitude) || Math.abs(longitude) > 180) return false;
        var utc = Instant.ofEpochMilli(captured).atOffset(ZoneOffset.UTC);
        double utcHour = utc.getHour() + utc.getMinute() / 60.0 + utc.getSecond() / 3600.0;
        // Longitude shifts mean solar noon; this avoids using the server timezone or DST.
        double localSolarHour = ((utcHour + longitude / 15) % 24 + 24) % 24;
        return localSolarHour >= 8 && localSolarHour < 17;
    }

    @Override
    public double selectionScore(MapillaryCandidateMatcher.Candidate candidate) {
        long now = Instant.now().toEpochMilli();
        long captured = candidate.image().path("captured_at").asLong(0);
        double freshness = 0;
        if (captured > 0 && captured <= now) {
            double years = (now - captured) / (365.25 * 24 * 60 * 60 * 1000);
            freshness = Math.max(0, 1 - years / 10);
        }
        return 0.8 * candidate.score() + 20 * freshness;
    }

    @Override
    public List<MapillaryCandidateMatcher.Candidate> order(List<MapillaryCandidateMatcher.Candidate> candidates) {
        List<MapillaryCandidateMatcher.Candidate> ranked = new ArrayList<>();
        for (var candidate : candidates) {
            if (isDaylightCapture(candidate.image())) ranked.add(candidate);
        }
        ranked.sort(Comparator.comparingDouble(this::selectionScore).reversed()
            .thenComparing(Comparator.comparingLong(
                (MapillaryCandidateMatcher.Candidate c) -> c.image().path("captured_at").asLong()).reversed())
            .thenComparing(c -> c.image().path("id").asText()));
        List<MapillaryCandidateMatcher.Candidate> diverse = new ArrayList<>();
        List<MapillaryCandidateMatcher.Candidate> nearby = new ArrayList<>();
        for (var candidate : ranked) {
            boolean similar = false;
            for (var previous : diverse) {
                if (sameViewpoint(candidate, previous)) {
                    similar = true;
                    break;
                }
            }
            if (similar) nearby.add(candidate);
            else diverse.add(candidate);
        }
        diverse.addAll(nearby);
        return diverse;
    }

    /** Groups nearby frames from the same capture period; later visits remain eligible. */
    private boolean sameViewpoint(MapillaryCandidateMatcher.Candidate first,
                                  MapillaryCandidateMatcher.Candidate second) {
        long firstTime = first.image().path("captured_at").asLong(0);
        long secondTime = second.image().path("captured_at").asLong(0);
        if (firstTime > 0 && secondTime > 0 && Math.abs(firstTime - secondTime) > 60_000) return false;
        var a = first.image().path("computed_geometry").path("coordinates");
        var b = second.image().path("computed_geometry").path("coordinates");
        double lat1 = Math.toRadians(a.get(1).asDouble());
        double lat2 = Math.toRadians(b.get(1).asDouble());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.get(0).asDouble() - a.get(0).asDouble());
        double h = Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        double meters = 2 * 6_371_008.8 * Math.asin(Math.sqrt(Math.min(1, h)));
        return meters < 8 && MapillaryCandidateMatcher.angleDifference(first.bearing(), second.bearing()) < 20;
    }
}
