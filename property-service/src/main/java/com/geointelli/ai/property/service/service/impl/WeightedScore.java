package com.geointelli.ai.property.service.service.impl;

/**
 * Weighted average of part scores that skips unknown (null) parts and rescales the remaining weights, so a
 * missing value neither lowers nor raises the result. Returns null when no part is known.
 */
final class WeightedScore {

    private WeightedScore() {
    }

    static Double average(double[] weights, Double... scores) {
        if (weights.length != scores.length) {
            throw new IllegalArgumentException("Every part score needs a weight");
        }
        double total = 0;
        double knownWeight = 0;
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] != null) {
                total += scores[i] * weights[i];
                knownWeight += weights[i];
            }
        }
        return knownWeight == 0 ? null : total / knownWeight;
    }
}
