package com.geointelli.ai.property.service.integration.mapillary;

import com.geointelli.ai.property.service.service.impl.MapillaryVisibilityServiceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;

/** Preserves source bytes and produces exactly one property-facing view for panoramas. */
public final class MapillaryImageProcessor {
    public static final String DETAIL_FIELDS = "id,geometry,computed_geometry,computed_compass_angle,computed_rotation,camera_type,exif_orientation,width,height,is_pano,captured_at,creator,thumb_original_url,thumb_2048_url,thumb_1024_url";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public static Map<String,Object> prepare(JsonNode image, double bearing, Path directory, String stem, Path cache) throws IOException, InterruptedException {
        boolean panorama = image.path("is_pano").asBoolean() || Set.of("spherical","equirectangular").contains(image.path("camera_type").asText());
        Map<String,Object> info = new LinkedHashMap<>();
        info.put("panorama", panorama);
        BufferedImage source = null; Path cached = null; String variant = null; boolean reused = false;
        List<String> failures = new ArrayList<>();
        for (String field : List.of("thumb_original_url","thumb_2048_url","thumb_1024_url")) {
            if (image.path(field).asText().isBlank()) continue;
            Path candidate = cache.resolve(image.path("id").asText() + "_" + field + ".source");
            try {
                reused = Files.isRegularFile(candidate);
                if (!reused) {
                    byte[] bytes = download(image.path(field).asText());
                    source = decode(bytes);
                    Path temporary = Files.createTempFile(cache,"source-",".tmp");
                    try { Files.write(temporary,bytes); Files.move(temporary,candidate,StandardCopyOption.REPLACE_EXISTING); }
                    finally { Files.deleteIfExists(temporary); }
                } else source = decode(Files.readAllBytes(candidate));
                cached=candidate; variant=field; break;
            } catch (IOException | IllegalArgumentException e) { failures.add(field); }
        }
        if (source == null || cached == null) throw new IOException("No permitted image variant could be downloaded and decoded");
        String format;
        try (var input = ImageIO.createImageInputStream(cached.toFile())) {
            var reader=ImageIO.getImageReaders(input).next();
            try { format=reader.getFormatName().toLowerCase(Locale.ROOT); } finally { reader.dispose(); }
        }
        String extension = format.equals("jpeg") ? "jpg" : format;
        String original = stem + (panorama ? "_original_panorama." : ".") + extension;
        Files.copy(cached,directory.resolve(original));
        if (!panorama) info.putAll(new MapillaryVisibilityServiceImpl().assess(source));
        info.put("originalFile",original); info.put("sourceVariant",variant); info.put("cacheReused",reused);
        info.put("sourceWidth",source.getWidth()); info.put("sourceHeight",source.getHeight());
        info.put("declaredOriginalWidth",image.path("width").asInt()); info.put("declaredOriginalHeight",image.path("height").asInt());
        info.put("matchesDeclaredOriginalResolution",source.getWidth()==image.path("width").asInt()
                && source.getHeight()==image.path("height").asInt());
        info.put("unavailableHigherVariants",failures); info.put("sourceBytesPreserved",true);
        if (!panorama) {
            info.put("file",original); info.put("qualityDecision","needs-manual-review");
            info.put("processing","original-photo-unchanged"); return info;
        }
        var extraction = PanoramaProjection.extract(source,image,bearing);
        info.putAll(extraction.assessment());
        if (extraction.view() != null) info.putAll(new MapillaryVisibilityServiceImpl().assess(extraction.view()));
        else { info.remove("visibilityScore"); info.remove("centralGreenFraction");
            info.remove("centralDarkFraction"); info.remove("verticalObstructionSignal"); }
        info.put("computedRotation",image.path("computed_rotation"));
        if (extraction.view() == null) info.put("file",null);
        else {
            String filename=stem+"_perspective.png";
            ImageIO.write(extraction.view(),"png",directory.resolve(filename).toFile());
            info.put("file",filename); // Only the perspective image may be a candidate photo.
        }
        return info;
    }
    private static byte[] download(String url) throws IOException, InterruptedException {
        URI uri=URI.create(url); String host=uri.getHost();
        if (!"https".equals(uri.getScheme()) || host==null || !(host.endsWith(".fbcdn.net")||host.endsWith(".mapillary.com")||host.equals("mapillary.com"))) {
            throw new IOException("Unexpected image host");
        }
        var response=HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input=response.body()) {
            if(response.statusCode()!=200) throw new IOException("Image HTTP error");
            byte[] bytes=input.readNBytes(100*1024*1024+1);
            if(bytes.length>100*1024*1024) throw new IOException("Source exceeds 100MiB limit");
            return bytes;
        }
    }
    private static BufferedImage decode(byte[] bytes) throws IOException {
        try (var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw new IOException("Unsupported source format");
            var reader=readers.next();
            try {
                reader.setInput(input);
                if((long)reader.getWidth(0)*reader.getHeight(0)>200_000_000) throw new IOException("Source exceeds 200MP decode limit");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
}
