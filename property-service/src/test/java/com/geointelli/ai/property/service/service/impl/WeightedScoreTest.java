package com.geointelli.ai.property.service.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WeightedScoreTest {

    private static final double[] WEIGHTS = {0.5, 0.25, 0.25};

    @Test
    @DisplayName("When every part is known, then return the plain weighted average")
    void whenEveryPartIsKnown_thenWeightedAverage() {
        assertEquals(62.5, WeightedScore.average(WEIGHTS, 100.0, 50.0, 0.0), 1e-9);
    }

    @Test
    @DisplayName("When a part is unknown, then rescale the remaining weights")
    void whenPartIsUnknown_thenRescaleWeights() {
        // (100 * 0.5 + 0 * 0.25) / 0.75
        assertEquals(200.0 / 3, WeightedScore.average(WEIGHTS, 100.0, null, 0.0), 1e-9);
    }

    @Test
    @DisplayName("When no part is known, then return null")
    void whenNoPartIsKnown_thenNull() {
        assertNull(WeightedScore.average(WEIGHTS, null, null, null));
    }

    @Test
    @DisplayName("When weights and parts do not match, then throw")
    void whenCountsDiffer_thenThrow() {
        assertThrows(IllegalArgumentException.class, () -> WeightedScore.average(WEIGHTS, 100.0));
    }
}
