package com.geointelli.ai.property.service.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Read access to the environmental reference data of SCRUM-201 (flood zones, census tracts, NRI ratings,
 * storm events, load history) for the risk section of the property location intelligence (SCRUM-127).
 * Every method is a read; the tables are not JPA entities, so the implementation uses native SQL.
 * A value the source does not hold comes back as null and is never replaced by a default.
 */
public interface RiskReferenceReader {

    /** Dataset names as stored in {@code reference_dataset_loads.dataset}. */
    String DATASET_FLOOD_ZONES = "FLOOD_ZONES";
    String DATASET_CENSUS_TRACTS = "CENSUS_TRACTS";
    String DATASET_NRI_TRACTS = "NRI_TRACTS";
    String DATASET_STORM_EVENTS = "STORM_EVENTS";

    /**
     * Point representing the property: a point guaranteed to lie on its parcel polygon, with the county.
     *
     * @param fips5 five-digit county FIPS code of the property's county; null when the county has none
     * @param lon   longitude (WGS 84)
     * @param lat   latitude (WGS 84)
     */
    record ParcelPoint(String fips5, double lon, double lat) {}

    /** One FEMA flood zone polygon containing the point (parts of the same polygon are merged). */
    record FloodZoneRow(String zone, String zoneSubtype, Boolean sfha, Double staticBfe, Double depth,
                        String studyType, String firmId) {}

    /** Census tract containing the point. */
    record TractRow(String tractFips, String name) {}

    /** One NRI hazard of a tract: score and rating as FEMA gives them, either may be null. */
    record HazardRow(String code, Double score, String rating) {}

    /** NRI row of a tract. */
    record NriRow(String version, Double riskScore, String riskRating, List<HazardRow> hazards) {}

    /** NOAA events of one type near the point. */
    record StormTypeRow(String eventType, int count, LocalDate lastEventDate, Long propertyDamageUsd) {}

    /**
     * Locates the property: the point on the surface of its parcel polygon and its county FIPS code.
     *
     * @param propertyId the property
     * @return empty when the property has no usable parcel polygon
     */
    Optional<ParcelPoint> parcelPoint(long propertyId);

    /**
     * Download date of the most recent load of a dataset.
     *
     * @param dataset one of the DATASET_ constants
     * @param fips5   county for a per-county dataset (flood zones); null for a statewide dataset
     * @return empty when the dataset was never loaded for that scope
     */
    Optional<LocalDate> loadedOn(String dataset, String fips5);

    /** Whether the flood map of a county has rows. */
    boolean floodZonesLoaded(String fips5);

    /**
     * FEMA flood zones containing the point, Special Flood Hazard Area polygons first. Usually one row; FEMA
     * polygons can overlap at their edges.
     */
    List<FloodZoneRow> floodZonesAt(String fips5, double lon, double lat);

    /** Census tract containing the point. */
    Optional<TractRow> tractAt(double lon, double lat);

    /** NRI ratings of a tract, hazards in the fixed order HRCN, CFLD, IFLD, TRND, WFIR, SWND, LTNG, HWAV. */
    Optional<NriRow> nri(String tractFips);

    /** Whether storm events exist for the state of a county (statewide file loaded). */
    boolean stormEventsLoaded(String fips5);

    /**
     * NOAA events with a location within the radius of the point, from the given year on, counted per
     * event type, most frequent first.
     */
    List<StormTypeRow> stormsNear(double lon, double lat, int radiusMeters, int fromYear);

    /** All NOAA events recorded for the county from the given year on, located or not. */
    int stormsInCounty(String fips5, int fromYear);
}
