package com.geointelli.ai.property.service.integration.mapillary;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import java.util.*;

public final class PanoramaProjection {
    public static final double HORIZONTAL_FOV = 60;
    public record Result(BufferedImage view, Map<String, Object> assessment) {}

    public static Result extract(BufferedImage panorama, JsonNode metadata, double targetBearing) {
        int sourceWidth = panorama.getWidth(), sourceHeight = panorama.getHeight();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("sourceWidth", sourceWidth); info.put("sourceHeight", sourceHeight);
        info.put("horizontalFovDegrees", HORIZONTAL_FOV); info.put("aspectRatio", "4:3");
        info.put("targetBearingDegrees", targetBearing); info.put("pitchDegrees", 0);
        info.put("upscaled", false); info.put("projection", "rectilinear-from-equirectangular");
        List<String> reasons = new ArrayList<>();
        info.put("unsuitableReasons", reasons);
        if (Math.abs(sourceWidth / (double) sourceHeight - 2) > 0.01) reasons.add("source-is-not-a-full-2-to-1-panorama");
        if (metadata.path("exif_orientation").asInt(0) != 1) reasons.add("unsupported-or-missing-exif-orientation");
        JsonNode vector = metadata.path("computed_rotation");
        if (vector.size() != 3 || !finiteVector(vector)) reasons.add("missing-or-invalid-camera-rotation");
        if (!Double.isFinite(targetBearing)) reasons.add("invalid-target-bearing");
        if (!reasons.isEmpty()) { info.put("qualityDecision", "unsuitable"); return new Result(null, info); }

        double[][] rotation = rodrigues(new double[]{vector.get(0).asDouble(), vector.get(1).asDouble(), vector.get(2).asDouble()});
        // Forward world direction = R^T * camera forward [0,0,1].
        double poseHeading = (Math.toDegrees(Math.atan2(rotation[2][0], rotation[2][1])) + 360) % 360;
        info.put("poseHeadingDegrees", poseHeading);
        if (!metadata.path("computed_compass_angle").isNumber()
                || MapillaryCandidateMatcher.angleDifference(poseHeading, metadata.path("computed_compass_angle").asDouble()) > 5) {
            reasons.add("camera-rotation-and-compass-conflict");
            info.put("qualityDecision", "unsuitable"); return new Result(null, info);
        }
        double halfFov = Math.toRadians(HORIZONTAL_FOV / 2);
        // Limit focal length to source pixels/radian. Thus even the center is not upsampled.
        int nativeWidth = (int) Math.floor(2 * Math.tan(halfFov)
                * Math.min(sourceWidth / (2 * Math.PI), sourceHeight / Math.PI));
        int width = Math.min(nativeWidth, setting("MAX_WIDTH", 1600));
        width = width / 4 * 4;
        if (width < 4) {
            reasons.add("source-too-small-to-project"); info.put("qualityDecision", "unsuitable"); return new Result(null, info);
        }
        int height = width * 3 / 4;
        double focal = width / (2 * Math.tan(halfFov));
        BufferedImage view = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double bearing = Math.toRadians(targetBearing), sin = Math.sin(bearing), cos = Math.cos(bearing);
        for (int y = 0; y < height; y++) {
            double up = -(y + 0.5 - height / 2.0) / focal;
            for (int x = 0; x < width; x++) {
                double right = (x + 0.5 - width / 2.0) / focal;
                double[] camera = multiply(rotation, new double[]{sin + right * cos, cos - right * sin, up});
                double u = Math.atan2(camera[0], camera[2]) / (2 * Math.PI) + 0.5;
                double v = 0.5 - Math.atan2(-camera[1], Math.hypot(camera[0], camera[2])) / Math.PI;
                view.setRGB(x, y, sample(panorama, u * sourceWidth - 0.5, v * sourceHeight - 0.5));
            }
        }
        double sharpness = laplacianVariance(view);
        double[] exposure = exposure(view);
        info.put("meanLuminance", exposure[0]);
        info.put("darkPixelFraction", exposure[1]);
        info.put("exposureAssessment", "central-region-heuristic-not-visibility-verification");
        if (exposure[0] < 55 && exposure[1] > 0.50) reasons.add("underexposed-scene");
        int minWidth = setting("MIN_WIDTH", 800), minHeight = setting("MIN_HEIGHT", 600);
        int minSharpness = setting("MIN_SHARPNESS", 20);
        int previewWidth = setting("PREVIEW_MIN_WIDTH", 640), previewHeight = setting("PREVIEW_MIN_HEIGHT", 480);
        boolean smallPreview = width < minWidth || height < minHeight;
        if (width < previewWidth || height < previewHeight) reasons.add("insufficient-native-preview-resolution");
        info.put("qualityTier", smallPreview ? "small-preview" : "detail-view");
        info.put("minimumPreviewWidth", previewWidth); info.put("minimumPreviewHeight", previewHeight);
        info.put("qualityWarnings", smallPreview ? List.of("below-detail-view-resolution") : List.of());
        if (sharpness < minSharpness) reasons.add("blur-suspected-low-edge-detail");
        info.put("viewWidth", width); info.put("viewHeight", height);
        info.put("nativeWidthLimit", nativeWidth); info.put("minimumWidth", minWidth); info.put("minimumHeight", minHeight);
        info.put("verticalFovDegrees", Math.toDegrees(2 * Math.atan(height / (2 * focal))));
        info.put("laplacianVariance", sharpness); info.put("minimumLaplacianVariance", minSharpness);
        info.put("sharpnessAssessment", "heuristic-not-a-proof-of-image-quality");
        info.put("qualityDecision", reasons.isEmpty() ? "needs-manual-review" : "unsuitable");
        return new Result(view, info);
    }

    private static int setting(String name, int fallback) {
        int value = Integer.parseInt(System.getenv().getOrDefault("MAPILLARY_PANORAMA_" + name, Integer.toString(fallback)));
        if (value < 1) throw new IllegalArgumentException("Panorama quality settings must be positive");
        return value;
    }
    private static boolean finiteVector(JsonNode node) {
        for (JsonNode n : node) if (!n.isNumber() || !Double.isFinite(n.asDouble())) return false;
        return true;
    }
    public static double[][] rodrigues(double[] vector) {
        double theta = Math.sqrt(vector[0]*vector[0] + vector[1]*vector[1] + vector[2]*vector[2]);
        if (theta < 1e-12) return new double[][]{{1,0,0},{0,1,0},{0,0,1}};
        double x = vector[0]/theta, y = vector[1]/theta, z = vector[2]/theta;
        double c = Math.cos(theta), s = Math.sin(theta), d = 1-c;
        return new double[][]{{c+x*x*d,x*y*d-z*s,x*z*d+y*s},
                {y*x*d+z*s,c+y*y*d,y*z*d-x*s},{z*x*d-y*s,z*y*d+x*s,c+z*z*d}};
    }
    private static double[] multiply(double[][] r, double[] v) {
        return new double[]{r[0][0]*v[0]+r[0][1]*v[1]+r[0][2]*v[2],
                r[1][0]*v[0]+r[1][1]*v[1]+r[1][2]*v[2], r[2][0]*v[0]+r[2][1]*v[1]+r[2][2]*v[2]};
    }
    private static int sample(BufferedImage image, double x, double y) {
        int rawX = (int)Math.floor(x), x0 = Math.floorMod(rawX, image.getWidth()), x1 = (x0+1)%image.getWidth();
        double clippedY = Math.max(0,Math.min(image.getHeight()-1,y));
        int y0 = (int)Math.floor(clippedY), y1 = Math.min(image.getHeight()-1,y0+1);
        double fx = x-rawX, fy = clippedY-y0;
        int a=image.getRGB(x0,y0), b=image.getRGB(x1,y0), c=image.getRGB(x0,y1), d=image.getRGB(x1,y1), rgb=0;
        for (int shift : new int[]{16,8,0}) {
            double top=((a>>shift)&255)*(1-fx)+((b>>shift)&255)*fx;
            double bottom=((c>>shift)&255)*(1-fx)+((d>>shift)&255)*fx;
            rgb |= ((int)Math.round(top*(1-fy)+bottom*fy))<<shift;
        }
        return rgb;
    }
    public static double laplacianVariance(BufferedImage image) {
        double sum=0, square=0; int count=0;
        // Central region excludes most empty sky and extreme projection edges.
        for (int y=Math.max(1,image.getHeight()/4);y<Math.min(image.getHeight()-1,image.getHeight()*9/10);y++) {
            for (int x=Math.max(1,image.getWidth()/5);x<Math.min(image.getWidth()-1,image.getWidth()*4/5);x++) {
                double lap=gray(image.getRGB(x-1,y))+gray(image.getRGB(x+1,y))
                        +gray(image.getRGB(x,y-1))+gray(image.getRGB(x,y+1))-4*gray(image.getRGB(x,y));
                sum+=lap; square+=lap*lap; count++;
            }
        }
        return count == 0 ? 0 : Math.max(0,square/count-Math.pow(sum/count,2));
    }
    /** Measures central-scene brightness so streetlights cannot hide a mostly dark facade. */
    private static double[] exposure(BufferedImage image) {
        double sum = 0; int dark = 0, count = 0;
        for (int y = image.getHeight() / 4; y < image.getHeight() * 9 / 10; y++) {
            for (int x = image.getWidth() / 5; x < image.getWidth() * 4 / 5; x++) {
                double luminance = gray(image.getRGB(x, y));
                sum += luminance;
                if (luminance < 40) dark++;
                count++;
            }
        }
        return count == 0 ? new double[]{0, 1} : new double[]{sum / count, dark / (double) count};
    }

    private static double gray(int rgb) { return .299*((rgb>>16)&255)+.587*((rgb>>8)&255)+.114*(rgb&255); }
}
