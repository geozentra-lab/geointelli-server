package com.geointelli.ai.property.service.integration.mapillary;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Queries Mapillary with bounded pagination and retries, without logging tokens. */
@Component
public class MapillaryApiClient {
    private final ObjectMapper json;
    private final String token;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public MapillaryApiClient(ObjectMapper json, @Value("${mapillary.access-token:}") String token) {
        this.json = json; this.token = token;
    }

    public boolean isConfigured() { return token != null && !token.isBlank(); }

    public JsonNode search(String bbox) throws IOException, InterruptedException {
        var images = json.createArrayNode();
        String after = null;
        for (int page = 0; page < 3; page++) {
            String url = "https://graph.mapillary.com/images?fields=" + MapillaryImageProcessor.DETAIL_FIELDS
                + "&limit=500&is_pano=true&bbox=" + bbox;
            if (after != null) url += "&after=" + URLEncoder.encode(after, StandardCharsets.UTF_8);
            JsonNode body = request(URI.create(url));
            if (!body.path("data").isArray()) throw new IOException("Missing Mapillary image data");
            for (JsonNode image : body.path("data")) images.add(image);
            JsonNode paging = body.path("paging");
            if (!paging.hasNonNull("next")) break;
            after = paging.path("cursors").path("after").asText(null);
            if (after == null || after.isBlank()) break;
        }
        return images;
    }

    private JsonNode request(URI uri) throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45))
                .header("Authorization", "OAuth " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body;
            try { body = json.readTree(response.body()); }
            catch (IOException invalidJson) { body = json.createObjectNode(); }
            if (body == null) body = json.createObjectNode();
            int code = body.path("error").path("code").asInt();
            boolean transientError = response.statusCode() == 429 || response.statusCode() >= 500
                || Set.of(4,17,32,613).contains(code);
            if (transientError && attempt < 2) {
                long seconds = 5L * (1L << attempt);
                try { seconds = Math.max(1, Math.min(60, Long.parseLong(response.headers().firstValue("Retry-After").orElse("")))); }
                catch (NumberFormatException ignored) { }
                Thread.sleep(seconds * 1000);
                continue;
            }
            if (response.statusCode() != 200 || body.has("error")) throw new ApiException();
            return body;
        }
    }

    /** Stops the batch after authentication, rate limit, or provider errors. */
    public static class ApiException extends IOException {
        public ApiException() { super("Mapillary API request failed"); }
    }
}
