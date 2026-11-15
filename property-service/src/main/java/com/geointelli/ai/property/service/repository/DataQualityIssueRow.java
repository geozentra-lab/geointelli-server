package com.geointelli.ai.property.service.repository;

/** One row returned by a data-quality rule query, with the provenance of the property (SCRUM-189). */
public interface DataQualityIssueRow {
    /** ID of the entity the issue points to (property, sale or assessment). */
    Long getEntityId();

    /** Property concerned by the issue. */
    Long getPropertyId();

    /** County folio of the property. */
    String getFolio();

    /** Human-readable explanation of the issue. */
    String getDetail();

    /** Source county from property_import_mapping, or null. */
    String getSourceCounty();

    /** Source state from property_import_mapping, or null. */
    String getSourceState();

    /** Property ID in the source file, or null. */
    Long getSourcePropertyId();
}
