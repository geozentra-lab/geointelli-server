package com.geointelli.ai.property.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Data-quality rules checked by the admin scan (SCRUM-189). Each rule names its category, the entity it
 * reports and the table it reads, so an issue reference {@code RULE:entityType:entityId} is reproducible.
 * Rules only report; they never change or delete data.
 */
@Getter
@AllArgsConstructor
public enum DataQualityRule {
    /** Property without a row in {@code addresses}. */
    PROPERTY_MISSING_ADDRESS(DataQualityCategory.MISSING, "PROPERTY", "addresses",
            "Property has no address"),
    /** Neither the property nor its parent property has a valid {@code parcels.geom} in SRID 4326. */
    PROPERTY_MISSING_PARCEL(DataQualityCategory.MISSING, "PROPERTY", "parcels",
            "Property has no usable parcel geometry"),
    /** Latest {@code assessments.year} of the property is older than the current year minus 2. */
    ASSESSMENT_STALE(DataQualityCategory.STALE, "PROPERTY", "assessments",
            "Latest assessment is older than 2 years"),
    /** {@code properties.lot_size} and {@code parcels.lot_size} differ by more than 10%; sizes under 100 are skipped. */
    LOT_SIZE_CONFLICT(DataQualityCategory.CONFLICTING, "PROPERTY", "properties, parcels",
            "Property and parcel lot sizes differ by more than 10% (sizes under 100 sq ft are placeholders)"),
    /** Qualified sale whose {@code sales.sale_price} is 0 or negative. */
    SALE_PRICE_OUTLIER(DataQualityCategory.OUTLIER, "SALE", "sales",
            "Qualified sale with a price of 0 or less"),
    /** Sale with the same property, deed (official record book and page), date and price as an older sale row. */
    SALE_DUPLICATE(DataQualityCategory.DUPLICATE, "SALE", "sales",
            "Same deed (book and page), sale date and price stored more than once for a property"),
    /** Assessment with the same property and year as an older assessment row. */
    ASSESSMENT_DUPLICATE(DataQualityCategory.DUPLICATE, "ASSESSMENT", "assessments",
            "Same property and year stored more than once");

    /** Kind of issue. */
    private final DataQualityCategory category;

    /** Entity whose ID appears in the issue reference: PROPERTY, SALE or ASSESSMENT. */
    private final String entityType;

    /** Table(s) the rule reads. */
    private final String source;

    /** Short explanation shown to administrators. */
    private final String description;
}
