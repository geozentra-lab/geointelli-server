package com.geointelli.ai.property.service.service.impl;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.service.MapillaryVisibilityService;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ranks central obstruction signals; green paint and building shadows can cause false penalties. */
@Service
public final class MapillaryVisibilityServiceImpl implements MapillaryVisibilityService {
    @Override
    public Map<String, Object> assess(BufferedImage image) {
        int left = image.getWidth() / 5, right = image.getWidth() * 4 / 5;
        int top = image.getHeight() / 4, bottom = image.getHeight() * 4 / 5;
        int green = 0, dark = 0, count = 0;
        int[] darkColumns = new int[right - left];
        double[] columnSum = new double[right - left], columnSquare = new double[right - left];
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                // Color is only a potential foliage signal, never a semantic vegetation label.
                if (g > r * 1.12 && g > b * 1.08 && g - Math.min(r, b) > 12) green++;
                double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
                columnSum[x - left] += luminance;
                columnSquare[x - left] += luminance * luminance;
                if (luminance < 45) { dark++; darkColumns[x - left]++; }
                count++;
            }
        }
        double vegetation = count == 0 ? 0 : green / (double) count;
        double darkness = count == 0 ? 0 : dark / (double) count;
        int narrowStripes = 0, run = 0;
        int neighborOffset = Math.max(2, image.getWidth() / 25);
        for (int index = 0; index < darkColumns.length; index++) {
            int column = darkColumns[index];
            double mean = columnSum[index] / Math.max(1, bottom - top);
            double variance = columnSquare[index] / Math.max(1, bottom - top) - mean * mean;
            int before = Math.max(0, index - neighborOffset), after = Math.min(darkColumns.length - 1, index + neighborOffset);
            double surrounding = (columnSum[before] + columnSum[after]) / (2 * Math.max(1, bottom - top));
            boolean flatContrastingStripe = variance < 250 && Math.abs(mean - surrounding) > 15;
            if (column > (bottom - top) * 0.8 || flatContrastingStripe) run++;
            else {
                if (run >= 2 && run <= image.getWidth() * 0.06) narrowStripes += run;
                run = 0;
            }
        }
        if (run >= 2 && run <= image.getWidth() * 0.06) narrowStripes += run;
        double vertical = Math.min(1, narrowStripes / (double) Math.max(1, image.getWidth() * 0.10));
        double score = 100 * Math.max(0, 1 - 0.50 * vegetation - 0.25 * darkness - 0.25 * vertical);
        List<String> warnings = new ArrayList<>();
        if (vegetation > 0.25) warnings.add("possible-central-foliage-or-green-surface");
        if (darkness > 0.45) warnings.add("dark-central-region-or-shadow");
        if (vertical > 0.25) warnings.add("possible-vertical-obstruction-or-building-edge");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("centralGreenFraction", vegetation); result.put("centralDarkFraction", darkness);
        result.put("verticalObstructionSignal", vertical); result.put("visibilityScore", score);
        result.put("visibilityWarnings", warnings);
        result.put("visibilityMethod", "central-color-darkness-and-column-heuristic-v2");
        result.put("visibilityMeaning", "ranking-only; does not detect a house, fence, tree or correct property identity");
        return result;
    }

    @Override
    public int rank(List<Map<String, Object>> candidates, int limit) {
        for (Map<String, Object> candidate : candidates) {
            boolean usable = candidate.get("file") != null && !"unsuitable".equals(candidate.get("qualityDecision"))
                && !"possible-intervening-parcel".equals(candidate.get("sightlineDecision"));
            double visibility = number(candidate, "visibilityScore");
            double geometry = number(candidate, "selectionScore");
            candidate.put("reviewEligible", usable);
            candidate.put("reviewScore", usable ? 0.75 * visibility + 0.25 * geometry : 0);
            candidate.put("selectedForReview", false);
        }
        candidates.sort(Comparator.comparing((Map<String, Object> c) -> Boolean.TRUE.equals(c.get("reviewEligible"))).reversed()
            .thenComparing(Comparator.comparingDouble((Map<String, Object> c) -> roadPriority(c)).reversed())
            .thenComparing(Comparator.comparingDouble((Map<String, Object> c) -> number(c, "reviewScore")).reversed())
            .thenComparing(c -> String.valueOf(c.get("imageId"))));
        int selected = 0, rank = 0;
        for (Map<String, Object> candidate : candidates) {
            candidate.put("rank", ++rank);
            if (selected < limit && candidate.get("file") != null
                    && !"unsuitable".equals(candidate.get("qualityDecision"))
                && !"possible-intervening-parcel".equals(candidate.get("sightlineDecision"))) {
                candidate.put("selectedForReview", true); selected++;
            }
        }
        return selected;
    }

    /** Severely penalized visibility does not force the address-facing side ahead of alternatives. */
    private double roadPriority(Map<String, Object> candidate) {
        return number(candidate, "visibilityScore") < 50 ? 0
            : candidate.get("roadPreferencePriority") instanceof Number priority ? priority.doubleValue() : 1;
    }

    private double number(Map<String, Object> values, String key) {
        return values.get(key) instanceof Number number ? number.doubleValue() : 0;
    }
}
