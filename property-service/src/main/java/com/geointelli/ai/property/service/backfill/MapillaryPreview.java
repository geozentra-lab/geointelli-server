package com.geointelli.ai.property.service.backfill;

import com.geointelli.ai.property.service.service.MapillaryCandidateSelectionService;
import com.geointelli.ai.property.service.service.impl.MapillaryCandidateSelectionServiceImpl;
import com.geointelli.ai.property.service.service.MapillaryVisibilityService;
import com.geointelli.ai.property.service.service.impl.MapillaryVisibilityServiceImpl;
import com.geointelli.ai.property.service.service.impl.MapillarySightlineServiceImpl;
import com.geointelli.ai.property.service.service.MapillaryParcelReviewService;
import com.geointelli.ai.property.service.service.impl.MapillaryParcelReviewServiceImpl;
import com.geointelli.ai.property.service.service.MapillaryRoadPreferenceService;
import com.geointelli.ai.property.service.service.impl.MapillaryRoadPreferenceServiceImpl;
import com.geointelli.ai.property.service.integration.mapillary.MapillaryCandidateMatcher;
import com.geointelli.ai.property.service.integration.mapillary.MapillaryImageProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Standalone, read-only validation. Does not start Spring or initialize AWS. */
public class MapillaryPreview {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private static final String FIELDS = MapillaryImageProcessor.DETAIL_FIELDS;
    record Property(long id, String folio, String address, String polygonWkt, String bbox) {}
    record Search(JsonNode images, int pages, boolean truncated) {}
    record Options(int limit, double radius, double maxDistance, double maxAngle, int candidates, int maxPages, Path output,
                   Long nearProperty, List<Long> excludedProperties, boolean panoramasOnly) {
        static Options parse(String[] args) {
            Map<String, String> values = new HashMap<>();
            Set<String> names = Set.of("properties", "radius", "max-distance", "max-angle", "candidates", "max-pages", "output",
                    "near-property", "exclude-properties", "panoramas-only");
            for (String arg : args) {
                if (arg.matches("[0-9]+")) values.put("properties", arg);
                else if (arg.startsWith("--") && arg.contains("=")) {
                    String[] entry = arg.substring(2).split("=", 2);
                    if (!names.contains(entry[0])) throw new IllegalArgumentException("Unknown option: " + entry[0]);
                    values.put(entry[0], entry[1]);
                } else throw new IllegalArgumentException("Use --option=value");
            }
            int limit = Integer.parseInt(values.getOrDefault("properties", "20"));
            double radius = Double.parseDouble(values.getOrDefault("radius", "80"));
            double distance = Double.parseDouble(values.getOrDefault("max-distance", "80"));
            double angle = Double.parseDouble(values.getOrDefault("max-angle", "35"));
            int candidates = Integer.parseInt(values.getOrDefault("candidates", "5"));
            int pages = Integer.parseInt(values.getOrDefault("max-pages", "3"));
            if (limit < 1 || limit > 100 || !Double.isFinite(radius) || radius < 1 || radius > 500
                    || !Double.isFinite(distance) || distance <= 0 || distance > radius
                    || !Double.isFinite(angle) || angle <= 0 || angle > 90
                    || candidates < 3 || candidates > 5 || pages < 1 || pages > 10) {
                throw new IllegalArgumentException("Properties 1..100; radius 1..500m; max-distance (0,radius]; max-angle (0,90]; candidates 3..5; max-pages 1..10");
            }
            Path output = Path.of(values.getOrDefault("output", "mapillary-validation/run-"
                    + Instant.now().toString().replace(":", "-") + "-" + UUID.randomUUID().toString().substring(0, 8)));
            Long near = values.containsKey("near-property") ? Long.parseLong(values.get("near-property")) : null;
            List<Long> excluded = values.containsKey("exclude-properties")
                    ? Arrays.stream(values.get("exclude-properties").split(",")).map(Long::parseLong).distinct().toList() : List.of();
            String pano = values.getOrDefault("panoramas-only", "false");
            if ((near != null && near < 1) || excluded.stream().anyMatch(id -> id < 1) || excluded.size() > 100
                    || !(pano.equals("true") || pano.equals("false"))) throw new IllegalArgumentException("Invalid nearby/panorama options");
            return new Options(limit, radius, distance, angle, candidates, pages, output, near, excluded, Boolean.parseBoolean(pano));
        }
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        String token = System.getenv("MAPILLARY_ACCESS_TOKEN");
        if (token == null || token.isBlank()) throw new IllegalStateException("MAPILLARY_ACCESS_TOKEN is missing");
        Path output = options.output().toAbsolutePath().normalize();
        if (Files.exists(output)) throw new IllegalArgumentException("Output already exists; choose a new directory");
        Files.createDirectories(output);
        Path cache = Path.of("mapillary-validation", ".cache").toAbsolutePath().normalize();
        Files.createDirectories(cache);
        List<Map<String, Object>> report = new ArrayList<>();
        MapillaryRoadPreferenceService roads = new MapillaryRoadPreferenceServiceImpl();
        Set<String> folders = new HashSet<>();
        try (Connection connection = openConnection()) {
            List<Property> properties = properties(connection, options);
            System.out.println("Mapillary validation: selected=" + properties.size() + ", radius=" + options.radius()
                    + "m, maxAngle=" + options.maxAngle() + ", output=" + output);
            boolean stopped = false;
            for (Property property : properties) {
                String folder = folderName(property.folio(), property.id());
                if (!folders.add(folder)) folder += "_property-" + property.id();
                Path directory = output.resolve(folder);
                Files.createDirectories(directory);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("propertyId", property.id()); row.put("folio", property.folio()); row.put("address", property.address());
                row.put("folder", folder); row.put("matchStatus", "unverified");
                row.put("nearPropertyId", options.nearProperty()); row.put("panoramasOnly", options.panoramasOnly());
                row.put("directionMeaning", options.panoramasOnly() ? "rendered-panorama-view" : "source-camera-heading");
                row.put("searchRadiusMeters", options.radius()); row.put("maxDistanceMeters", options.maxDistance());
                row.put("maxAngleDifferenceDegrees", options.maxAngle());
                row.put("scoreFormula", "100 * (0.75 * (1 - angle/maxAngle) + 0.25 * (1 - distance/maxDistance))");
                row.put("targetMethod", "interior-point-of-nearest-parcel-component"); row.put("candidates", new ArrayList<>());
                if (stopped) row.put("status", "not-processed-api-stopped");
                else if (property.polygonWkt() == null) {
                    row.put("status", "no-suitable-image"); row.put("reason", "missing-valid-parcel-geometry");
                } else {
                    try {
                        MapillaryParcelReviewService parcelReview = new MapillaryParcelReviewServiceImpl();
                        try {
                            row.put("parcelReview", parcelReview.inspect(connection, property.id()));
                        } catch (SQLException reviewError) {
                            row.put("parcelReview", Map.of("associationDecision", "review-evidence-unavailable",
                                "errorType", reviewError.getClass().getSimpleName()));
                        }
                        Search search = search(property, token, options.maxPages(), options.panoramasOnly());
                        row.put("nearbyImagesReturned", search.images().size()); row.put("searchPages", search.pages());
                        row.put("searchTruncated", search.truncated());
                        var matches = MapillaryCandidateMatcher.match(connection, property.polygonWkt(), search.images(),
                                options.maxDistance(), options.maxAngle(), options.panoramasOnly());
                        row.put("rejections", matches.rejected()); row.put("eligibleCandidates", matches.candidates().size());
                        List<Map<String, Object>> downloaded = new ArrayList<>();
                        List<Map<String, String>> failures = new ArrayList<>();
                        MapillaryCandidateSelectionService selection = new MapillaryCandidateSelectionServiceImpl();
                        int rankedPosition = 0, usable = 0;
                        int maxAttempts = 30;
                        int evaluationTarget = options.candidates() * 3;
                        List<Map<String, Object>> sightlineSkipped = new ArrayList<>();
                        row.put("capturePolicy", "daytime-only: estimated local solar time 08:00-17:00; missing capture metadata rejected");
                        row.put("selectionFormula", "80% geometry + 20% freshness (linear decay over 10 years)");
                        row.put("viewpointDiversity", "Prefer camera positions 8m apart or bearings 20 degrees apart; nearby frames within 60s are fallbacks");
                        row.put("maxCandidateAttempts", maxAttempts);
                        Map<String, Map<String, Object>> roadEvidence = new HashMap<>();
                        List<MapillaryCandidateMatcher.Candidate> orderedCandidates = selection.order(matches.candidates());
                        for (var candidate : orderedCandidates) roadEvidence.put(candidate.image().path("id").asText(),
                            roads.assess(property.address(), candidate.image().path("computed_geometry").path("coordinates"),
                                candidate.targetLongitude(), candidate.targetLatitude()));
                        orderedCandidates.sort(Comparator.comparingInt((MapillaryCandidateMatcher.Candidate c) ->
                            ((Number)roadEvidence.get(c.image().path("id").asText()).get("roadPreferencePriority")).intValue()).reversed());
                        for (var candidate : orderedCandidates) {
                            if (usable >= evaluationTarget || rankedPosition >= maxAttempts) break;
                            rankedPosition++;
                            JsonNode image = candidate.image(); String id = image.path("id").asText();
                            try {
                                Map<String,Object> sightline = new MapillarySightlineServiceImpl().assess(
                                    (Map<String, Object>)row.get("parcelReview"), image.path("computed_geometry").path("coordinates"),
                                    candidate.targetLongitude(), candidate.targetLatitude());
                                if ("possible-intervening-parcel".equals(sightline.get("sightlineDecision"))) {
                                    sightline.put("imageId", id); sightlineSkipped.add(sightline);
                                    continue;
                                }
                                Map<String,Object> processing = MapillaryImageProcessor.prepare(image, candidate.bearing(),
                                        directory, "candidate_" + rankedPosition, cache);
                                Map<String, Object> info = new LinkedHashMap<>();
                                info.putAll(processing);
                                info.putAll(sightline);
                                info.putAll(roadEvidence.get(id));
                                info.put("rank", downloaded.size() + 1); info.put("eligibleRank", rankedPosition);
                                info.put("imageId", id);
                                info.put("selectionScore", selection.selectionScore(candidate));
                                info.put("score", candidate.score()); info.put("distanceMeters", candidate.distance());
                                info.put("cameraDirectionDegrees", candidate.heading()); info.put("targetBearingDegrees", candidate.bearing());
                                info.put("sourceCameraDirectionDegrees", image.path("computed_compass_angle").asDouble());
                                info.put("sourceAngleDifferenceDegrees", MapillaryCandidateMatcher.angleDifference(
                                        image.path("computed_compass_angle").asDouble(), candidate.bearing()));
                                info.put("angleDifferenceDegrees", candidate.angle());
                                info.put("cameraCoordinates", image.path("computed_geometry").path("coordinates"));
                                info.put("originalCameraCoordinates", image.path("geometry").path("coordinates"));
                                info.put("targetLongitude", candidate.targetLongitude()); info.put("targetLatitude", candidate.targetLatitude());
                                info.put("panorama", image.path("is_pano").asBoolean());
                                info.put("capturedAt", image.path("captured_at").isNumber()
                                        ? Instant.ofEpochMilli(image.path("captured_at").asLong()).toString() : null);
                                info.put("contributor", image.path("creator").path("username").asText("unknown"));
                                info.put("sourceUrl", "https://www.mapillary.com/app/?pKey=" + id); info.put("license", "CC BY-SA 4.0");
                                downloaded.add(info);
                                if (info.get("file") != null && !"unsuitable".equals(info.get("qualityDecision"))
                                        && !"possible-intervening-parcel".equals(info.get("sightlineDecision"))) usable++;
                            } catch (IOException | IllegalArgumentException e) {
                                failures.add(Map.of("imageId", id, "errorType", e.getClass().getSimpleName()));
                            }
                        }
                        row.put("sightlineSkippedCandidates", sightlineSkipped);
                        row.put("evaluatedUsableCandidates", usable);
                        MapillaryVisibilityService visibility = new MapillaryVisibilityServiceImpl();
                        usable = visibility.rank(downloaded, options.candidates());
                        row.put("reviewFormula", "Address-street preference first, then 75% visibility heuristic + 25% geometric/freshness selection score");
                        row.put("evaluationTarget", evaluationTarget);
                        row.put("candidates", downloaded); row.put("downloadFailures", failures);
                        row.put("attemptedCandidates", rankedPosition); row.put("usableCandidates", usable);
                        row.put("candidateSearchLimited", ((Number)row.get("evaluatedUsableCandidates")).intValue() < evaluationTarget && rankedPosition < matches.candidates().size());
                        row.put("status", !downloaded.isEmpty() ? downloaded.stream().anyMatch(i -> !"unsuitable".equals(i.get("qualityDecision")))
                                ? "needs-review" : "no-suitable-image"
                                : matches.candidates().isEmpty() ? "no-suitable-image" : "download-failed");
                    } catch (ApiException e) {
                        row.put("status", "api-error"); row.put("httpStatus", e.status); row.put("apiErrorCode", e.code); stopped = true;
                    } catch (IOException e) {
                        row.put("status", "network-error"); row.put("errorType", e.getClass().getSimpleName());
                    }
                }
                report.add(row);
                JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("metadata.json").toFile(), row);
                saveReport(output, report);
                System.out.println("property=" + property.id() + " folio=" + property.folio() + " status=" + row.get("status")
                        + " candidates=" + ((List<?>) row.get("candidates")).size());
                if (!stopped) Thread.sleep(1000);
            }
            saveReport(output, report);
            System.out.println("Mapillary validation finished: processed=" + report.size() + ", report=" + output.resolve("report.html"));
        }
    }

    static Connection openConnection() throws SQLException {
        try { Class.forName("org.postgresql.Driver"); }
        catch (ClassNotFoundException e) { throw new SQLException("PostgreSQL JDBC driver is missing", e); }
        var yaml = new YamlPropertiesFactoryBean(); yaml.setResources(new ClassPathResource("application-local.yml"));
        var settings = yaml.getObject();
        String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL", settings.getProperty("spring.datasource.url"));
        String user = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", System.getenv("DB_USER"));
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", System.getenv("DB_PASSWORD"));
        Connection connection = DriverManager.getConnection(url, user, password);
        try (Statement statement = connection.createStatement()) { statement.execute("SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY"); }
        connection.setReadOnly(true);
        return connection;
    }

    private static List<Property> properties(Connection connection, Options options) throws SQLException {
        List<Property> result = new ArrayList<>();
        String nearbyFilter = options.nearProperty() == null ? "" : "AND p.id IN (SELECT property_id FROM nearby)";
        String parcelJoin = options.nearProperty() != null ? "JOIN nearby_parcels parcel ON parcel.property_id = p.id" : """
            LEFT JOIN LATERAL (
                SELECT ST_UnaryUnion(ST_Collect(pa.geom)) AS geom FROM parcels pa
                WHERE pa.property_id = p.id AND pa.geom IS NOT NULL AND NOT ST_IsEmpty(pa.geom)
                  AND ST_IsValid(pa.geom) AND ST_SRID(pa.geom) = 4326
            ) parcel ON true
            """;
        try (PreparedStatement query = connection.prepareStatement("""
            WITH anchor AS MATERIALIZED (
                SELECT ST_UnaryUnion(ST_Collect(geom)) AS geom FROM parcels
                WHERE property_id = ? AND geom IS NOT NULL AND ST_IsValid(geom) AND NOT ST_IsEmpty(geom) AND ST_SRID(geom)=4326
            ), search_bounds AS MATERIALIZED (
                SELECT ST_Envelope(ST_Buffer(geom::geography, 500)::geometry) AS box FROM anchor
            ), nearby AS MATERIALIZED (
                SELECT DISTINCT pa.property_id FROM parcels pa CROSS JOIN search_bounds b
                WHERE pa.geom && b.box AND pa.property_id IS NOT NULL
            ), nearby_parcels AS MATERIALIZED (
                SELECT pa.property_id, ST_UnaryUnion(ST_Collect(pa.geom)) AS geom
                FROM parcels pa JOIN nearby n ON n.property_id = pa.property_id
                WHERE pa.geom IS NOT NULL AND NOT ST_IsEmpty(pa.geom) AND ST_IsValid(pa.geom) AND ST_SRID(pa.geom)=4326
                GROUP BY pa.property_id
            )
            SELECT p.id, p.folio, a.address, ST_AsText(parcel.geom) AS polygon,
                   ST_XMin(bounds.box) || ',' || ST_YMin(bounds.box) || ',' || ST_XMax(bounds.box) || ',' || ST_YMax(bounds.box) AS bbox
            FROM properties p JOIN addresses a ON a.property_id = p.id
            JOIN counties c ON c.id = p.county_id JOIN states s ON s.id = c.state_id
            %s
            LEFT JOIN LATERAL (SELECT ST_Envelope(ST_Buffer(parcel.geom::geography, ?)::geometry) AS box) bounds ON true
            WHERE LOWER(c.name) = 'miami-dade' AND s.code = 'FL'
              AND a.address IS NOT NULL AND TRIM(a.address) <> ''
              AND NOT EXISTS (SELECT 1 FROM property_images i WHERE i.property_id = p.id)
              %s
              AND p.id <> ALL (CAST(? AS bigint[]))
              AND (CAST(? AS bigint) IS NULL OR ST_DWithin(parcel.geom::geography, (SELECT geom FROM anchor)::geography, 500))
            ORDER BY CASE WHEN CAST(? AS bigint) IS NULL THEN 0
                          ELSE ST_Distance(parcel.geom::geography, (SELECT geom FROM anchor)::geography) END, p.id LIMIT ?
            """.formatted(parcelJoin, nearbyFilter))) {
            query.setObject(1, options.nearProperty(), Types.BIGINT);
            query.setDouble(2, options.radius());
            query.setArray(3, connection.createArrayOf("bigint", options.excludedProperties().toArray(Long[]::new)));
            query.setObject(4, options.nearProperty(), Types.BIGINT); query.setObject(5, options.nearProperty(), Types.BIGINT);
            query.setInt(6, options.limit()); query.setQueryTimeout(120);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(new Property(rows.getLong("id"), rows.getString("folio"),
                        rows.getString("address"), rows.getString("polygon"), rows.getString("bbox")));
            }
        }
        return result;
    }

    private static Search search(Property property, String token, int maxPages, boolean panoramasOnly) throws IOException, InterruptedException {
        var images = JSON.createArrayNode(); String after = null; boolean truncated; int pages = 0;
        do {
            String url = "https://graph.mapillary.com/images?fields=" + FIELDS + "&limit=500&bbox=" + property.bbox();
            if (panoramasOnly) url += "&is_pano=true";
            if (after != null) url += "&after=" + URLEncoder.encode(after, StandardCharsets.UTF_8);
            JsonNode body = apiJson(URI.create(url), token);
            if (!body.path("data").isArray()) throw new IOException("Mapillary response has no data array");
            for (JsonNode image : body.path("data")) images.add(image);
            pages++; truncated = body.path("paging").hasNonNull("next"); after = nextCursor(body.path("paging"));
        } while (truncated && after != null && pages < maxPages);
        return new Search(images, pages, truncated);
    }

    static String nextCursor(JsonNode paging) {
        String after = paging.path("cursors").path("after").asText(null);
        if (after != null && !after.isBlank()) return after;
        // Rebuild our own request; never follow or persist a URL containing a token.
        try {
            String query = URI.create(paging.path("next").asText("")).getRawQuery();
            if (query != null) for (String field : query.split("&")) {
                String[] pair = field.split("=", 2);
                if (pair.length == 2 && pair[0].equals("after")) return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    static JsonNode apiJson(URI uri, String token) throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            var response = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45))
                    .header("Authorization", "OAuth " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body;
            try { body = JSON.readTree(response.body()); }
            catch (IOException e) { body = JSON.createObjectNode(); }
            if (body == null) body = JSON.createObjectNode();
            int code = body.path("error").path("code").asInt();
            boolean rateLimited = response.statusCode() == 429 || Set.of(4, 17, 32, 613).contains(code);
            if ((rateLimited || response.statusCode() >= 500) && attempt < 2) {
                long delay = retryDelay(response.headers().firstValue("Retry-After").orElse(""), attempt);
                System.out.println("Mapillary rate limit/transient error; retrying in " + delay + " seconds");
                Thread.sleep(delay * 1000); continue;
            }
            if (response.statusCode() != 200 || body.has("error")) throw new ApiException(response.statusCode(), code);
            return body;
        }
    }
    static long retryDelay(String retryAfter, int attempt) {
        try { return Math.max(1, Math.min(60, Long.parseLong(retryAfter))); }
        catch (NumberFormatException ignored) {
            try { return Math.max(1, Math.min(60, Duration.between(Instant.now(), ZonedDateTime.parse(retryAfter,
                    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds())); }
            catch (java.time.format.DateTimeParseException ignoredDate) { return 5L * (1L << attempt); }
        }
    }

    static String folderName(String folio, long id) {
        if (folio == null || folio.isBlank()) return "property-" + id;
        String folder = folio.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return folder.substring(0, Math.min(100, folder.length()));
    }
    private static void saveReport(Path output, List<Map<String, Object>> report) throws IOException {
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("report.json").toFile(), report);
        Files.writeString(output.resolve("report.html"), gallery(report), StandardCharsets.UTF_8);
    }
    static String gallery(List<Map<String, Object>> rows) {
        StringBuilder html = new StringBuilder("<!doctype html><html lang='en'><meta charset='utf-8'><title>Mapillary validation</title>"
                + "<style>body{font:16px system-ui;margin:32px;background:#f6f7f9;color:#172033}"
                + ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:16px}"
                + "figure{background:white;padding:14px;margin:0;border:1px solid #ddd;border-radius:8px}"
                + "img{width:100%;height:250px;object-fit:contain;background:#eee}article{margin:35px 0}"
                + "figcaption{line-height:1.6}a{color:#1250aa}details{margin:12px 0}</style><h1>Mapillary local validation</h1>"
                + "<p>Geometry: direction 75%, distance 25%. Download order additionally considers capture freshness and viewpoint diversity. Scores are heuristics, not property-match confidence.</p>"
                + "<p>Daytime-only selection uses an estimated local solar-time window, with exposure screening. All matches are unverified. Check the address, house visibility, image age and GPS accuracy. "
                + "Panoramas are shown only as target-facing, 60-degree, 4:3 perspective views. Originals are linked for comparison. "
                + "Unsuitable views are diagnostic previews, not accepted property photos. Click an image for its native resolution.</p>");
        long withImages = rows.stream().filter(r -> !((List<?>) r.get("candidates")).isEmpty()).count();
        html.append("<p>Properties: ").append(rows.size()).append("; with candidates: ").append(withImages).append(".</p>");
        for (Map<String, Object> row : rows) {
            html.append("<article><h2>Folio ").append(escape(row.get("folio"))).append(" &mdash; ")
                    .append(escape(row.get("address"))).append("</h2><p>Status: ").append(escape(row.get("status")))
                    .append("; property ID: ").append(row.get("propertyId")).append(". <a href='")
                    .append(escape(row.get("folder"))).append("/metadata.json'>Scoring metadata</a></p>");
            if (Boolean.TRUE.equals(row.get("panoramasOnly"))) html.append("<p>Direction metrics refer to the projected view, aimed at the parcel. Source camera heading is the panorama reference orientation.</p>");
            if (row.containsKey("reason")) html.append("<p>").append(escape(row.get("reason"))).append("</p>");
            html.append("<details><summary>Filtering details</summary><pre>").append(escape(row.get("rejections")))
                    .append("</pre><p>Search truncated: ").append(escape(row.get("searchTruncated"))).append("</p></details><div class='grid'>");
            for (Object item : (List<?>) row.get("candidates")) {
                Map<?, ?> image = (Map<?, ?>) item;
                html.append("<figure>");
                if (image.get("file") != null) {
                    String file = escape(row.get("folder")) + "/" + escape(image.get("file"));
                    html.append("<a href='").append(file).append("'><img loading='lazy' src='").append(file)
                            .append("' alt='Mapillary candidate ").append(escape(image.get("imageId"))).append("'></a>");
                } else html.append("<p>No trustworthy perspective view could be rendered.</p>");
                html.append("<figcaption><strong>#").append(image.get("rank")).append(" &mdash; Score ").append(number(image.get("score"))).append(" / 100</strong><br>")
                        .append("Selected for review: ").append(escape(image.get("selectedForReview")))
                        .append("<br>Review score: ").append(number(image.get("reviewScore")))
                        .append("<br>Visibility heuristic: ").append(number(image.get("visibilityScore")))
                        .append("<br>Address street: ").append(escape(image.get("matchedAddressStreet")))
                        .append("; camera street preference: ").append(escape(image.get("roadPreference")))
                        .append("<br>Sightline: ").append(escape(image.get("sightlineDecision")))
                        .append("; intervening parcels: ").append(escape(image.get("interveningPropertyIds")))
                        .append("<br>Visibility warnings: ").append(escape(image.get("visibilityWarnings")))
                        .append("<br>Selection score: ").append(number(image.get("selectionScore")))
                        .append("<br>Quality tier: ").append(escape(image.get("qualityTier")))
                        .append("<br>Warnings: ").append(escape(image.get("qualityWarnings")))
                        .append("<br>Quality decision: ").append(escape(image.get("qualityDecision")))
                        .append("<br>Mean luminance: ").append(number(image.get("meanLuminance")))
                        .append("; dark pixel fraction: ").append(number(image.get("darkPixelFraction")))
                        .append("<br>Quality flags: ").append(escape(image.get("unsuitableReasons")))
                        .append("<br>Source: ").append(escape(image.get("sourceWidth"))).append(" x ").append(escape(image.get("sourceHeight")))
                        .append("; view: ").append(escape(image.get("viewWidth"))).append(" x ").append(escape(image.get("viewHeight")))
                        .append("<br><a href='").append(escape(row.get("folder"))).append("/").append(escape(image.get("originalFile")))
                        .append("'>Original source for comparison</a><br>")
                        .append("Folio: ").append(escape(row.get("folio"))).append("<br>Image ID: ").append(escape(image.get("imageId")))
                        .append("<br>Distance to parcel: ").append(number(image.get("distanceMeters"))).append(" m")
                        .append("<br>Camera direction: ").append(number(image.get("cameraDirectionDegrees"))).append("&deg;")
                        .append("<br>Source camera direction: ").append(number(image.get("sourceCameraDirectionDegrees"))).append("&deg;")
                        .append("<br>Source angle to parcel: ").append(number(image.get("sourceAngleDifferenceDegrees"))).append("&deg;")
                        .append("<br>Target bearing: ").append(number(image.get("targetBearingDegrees"))).append("&deg;")
                        .append("<br>Angle difference: ").append(number(image.get("angleDifferenceDegrees"))).append("&deg;")
                        .append("<br>Panorama: ").append(escape(image.get("panorama"))).append("<br>Captured: ").append(escape(image.get("capturedAt")))
                        .append("<br>Photo by ").append(escape(image.get("contributor"))).append(" on <a href='").append(escape(image.get("sourceUrl")))
                        .append("'>Mapillary</a>; <a href='https://creativecommons.org/licenses/by-sa/4.0/'>CC BY-SA 4.0</a>. ")
                        .append(Boolean.TRUE.equals(image.get("panorama")) ? "Perspective projected from source panorama." : "Original photo unchanged.")
                        .append("</figcaption></figure>");
            }
            html.append("</div></article>");
        }
        return html.append("</html>").toString();
    }
    private static String number(Object value) { return value instanceof Number n ? String.format(Locale.ROOT, "%.2f", n.doubleValue()) : "n/a"; }
    private static String escape(Object value) {
        return String.valueOf(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static class ApiException extends IOException {
        final int status, code;
        ApiException(int status, int code) { super("Mapillary API HTTP " + status + ", code " + code); this.status = status; this.code = code; }
    }
}
