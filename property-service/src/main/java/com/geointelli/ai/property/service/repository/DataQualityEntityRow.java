package com.geointelli.ai.property.service.repository;

/** Existence check of the entity a data-quality finding points to, with its property (SCRUM-190). */
public interface DataQualityEntityRow {
    /** Property concerned: the property itself, or the property of the sale or assessment; may be null. */
    Long getPropertyId();
}
