package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.MapillarySelectionDTO;
import com.geointelli.ai.property.service.integration.mapillary.*;
import com.geointelli.ai.property.service.repository.ImageBackfillPropertyRepository;
import com.geointelli.ai.property.service.service.*;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/** Reuses the preview's Java filtering and ranking without running SegFormer. */
@Service
@RequiredArgsConstructor
public class MapillaryImageSelectionServiceImpl implements MapillaryImageSelectionService {
    private final ImageBackfillPropertyRepository properties;
    private final MapillaryApiClient api;
    private final MapillaryCandidateSelectionService selection;
    private final MapillaryVisibilityService visibility;
    private final MapillarySightlineService sightlines;
    private final MapillaryParcelReviewService parcels;
    private final MapillaryRoadPreferenceService roads;
    private final ObjectMapper json;

    @Override
    public MapillarySelectionDTO select(Connection connection, long propertyId, Path directory) throws Exception {
        var property = properties.load(connection, propertyId);
        if (property == null || property.polygon() == null) return null;
        // Address evidence is optional; parcel geometry still anchors image selection.
        Map<String, Object> parcel = parcels.inspect(connection, propertyId);
        if (!"linked-parcel-consistent-house-identity-unverified".equals(parcel.get("associationDecision"))) return null;
        var matches = MapillaryCandidateMatcher.match(connection, property.polygon(), api.search(property.bbox()), 80, 35, true);
        var candidates = selection.order(matches.candidates());
        Map<String, Map<String, Object>> roadEvidence = new HashMap<>();
        for (var candidate : candidates) {
            roadEvidence.put(candidate.image().path("id").asText(), roads.assess(property.address(),
                candidate.image().path("computed_geometry").path("coordinates"), candidate.targetLongitude(), candidate.targetLatitude()));
        }
        candidates.sort(Comparator.comparingInt((MapillaryCandidateMatcher.Candidate candidate) ->
            ((Number) roadEvidence.get(candidate.image().path("id").asText()).get("roadPreferencePriority")).intValue()).reversed());
        List<Map<String, Object>> evaluated = new ArrayList<>();
        Path cache = directory.resolve("sources"); Files.createDirectories(cache);
        int attempts = 0, usable = 0, downloadFailures = 0;
        for (var candidate : candidates) {
            if (attempts >= 30 || usable >= 9) break;
            attempts++;
            var image = candidate.image();
            var sightline = sightlines.assess(parcel, image.path("computed_geometry").path("coordinates"),
                candidate.targetLongitude(), candidate.targetLatitude());
            if ("possible-intervening-parcel".equals(sightline.get("sightlineDecision"))) continue;
            Map<String, Object> info;
            try {
                info = MapillaryImageProcessor.prepare(image, candidate.bearing(), directory, "candidate-" + attempts, cache);
            } catch (IOException | IllegalArgumentException error) {
                downloadFailures++;
                continue;
            }
            info.putAll(sightline); info.putAll(roadEvidence.get(image.path("id").asText()));
            info.put("selectionScore", selection.selectionScore(candidate));
            info.put("imageId", image.path("id").asText());
            info.put("contributor", image.path("creator").path("username").asText("unknown"));
            info.put("capturedAt", image.path("captured_at").asLong());
            evaluated.add(info);
            if (info.get("file") != null && !"unsuitable".equals(info.get("qualityDecision"))) usable++;
        }
        if (visibility.rank(evaluated, 1) == 0) {
            if (downloadFailures > 0) throw new IOException("Candidate downloads failed");
            return null;
        }
        Map<String, Object> best = evaluated.get(0);
        String filename = (String) best.get("file");
        MapillarySelectionDTO selected = new MapillarySelectionDTO();
        selected.setContent(Files.readAllBytes(directory.resolve(filename)));
        selected.setExtension(filename.substring(filename.lastIndexOf('.') + 1));
        selected.setSourceImageId((String) best.get("imageId"));
        selected.setSourceUrl("https://www.mapillary.com/app/?pKey=" + selected.getSourceImageId());
        selected.setContributor((String) best.get("contributor"));
        selected.setCapturedAt(Instant.ofEpochMilli(((Number) best.get("capturedAt")).longValue()));
        // Keep source attribution and ranking evidence; never mark a match as verified.
        best.remove("file"); best.remove("originalFile");
        selected.setProcessingNote(json.writeValueAsString(best));
        return selected;
    }
}
