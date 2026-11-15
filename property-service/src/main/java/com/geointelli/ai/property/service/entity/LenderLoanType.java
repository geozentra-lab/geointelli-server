package com.geointelli.ai.property.service.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Recorded loan program of an application; UNKNOWN means no loan type was supplied. */
public enum LenderLoanType {
    CONVENTIONAL, FHA, VA, USDA, UNKNOWN;

    /**
     * Reads a loan type from JSON by its name only. Without this, Jackson also accepts a number as the position in
     * this list (3 was stored as USDA), which would silently put an application under the wrong type in the loan mix.
     *
     * @param name the loan type name, for example FHA; surrounding spaces are ignored
     * @return the loan type
     * @throws IllegalArgumentException if the value is not one of the names (returned to the client as 400)
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static LenderLoanType fromJson(String name) {
        return valueOf(name.trim());
    }
}
