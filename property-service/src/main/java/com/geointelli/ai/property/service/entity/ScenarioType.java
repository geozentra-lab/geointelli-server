package com.geointelli.ai.property.service.entity;

/** Kinds of saved scenarios (SCRUM-147). Only types that a shared calculation service can recalculate are listed. */
public enum ScenarioType {
    /** Residential investment analysis, recalculated by POST /api/calculations/investment (SCRUM-64). */
    INVESTMENT
}
