package com.geointelli.ai.property.service.entity;

/** Smallest geography a provider verifies for a rent observation (SCRUM-132). */
public enum RentGeographyLevel {
    /** Two-letter state code, for example {@code FL}. */
    STATE(2),
    /** Five-digit county FIPS code, for example {@code 12086}. */
    COUNTY(5),
    /** Five-digit ZIP code. */
    ZIP(5),
    /** Eleven-digit census tract FIPS code, for example {@code 12086000107}. */
    TRACT(11);

    private final int codeLength;

    RentGeographyLevel(int codeLength) {
        this.codeLength = codeLength;
    }

    /** Exact length of a valid code at this level. */
    public int codeLength() {
        return codeLength;
    }
}
