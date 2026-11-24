package com.geointelli.ai.property.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * Risk section of the property location intelligence (SCRUM-127 / SCRUM-53), read from the environmental
 * reference data of SCRUM-201. Three independent blocks, each with its own status, source and data date, so a
 * source that is missing never hides the others: the FEMA flood zone at the parcel point, the FEMA National
 * Risk Index ratings of the census tract containing the point, and the NOAA storm events recorded near the point.
 * Nothing is scored or rated by GeoZentra: a value the source does not give stays null and is described as
 * unknown, never as "low" or "safe".
 */
@Data
public class RiskContextDTO {

    /** The property has a parcel polygon and at least one block has evidence. */
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    /** The property has no parcel polygon, so no block can be evaluated. */
    public static final String STATUS_NO_COVERAGE = "NO_COVERAGE";
    /** The reference tables could not be read. */
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    /** Block status: the source has no row for this location; it is not evidence of low risk. */
    public static final String STATUS_NO_DATA = "NO_DATA";

    public static final String DISTANCE_UNIT = "m";
    public static final String SOURCE_FLOOD = "FEMA National Flood Hazard Layer (NFHL), flood hazard zones";
    public static final String SOURCE_HAZARDS = "FEMA National Risk Index (NRI), census tract";
    public static final String SOURCE_STORMS = "NOAA NCEI Storm Events Database";

    /** Radius around the parcel point for storm events with a location. */
    public static final int STORM_RADIUS_M = 10_000;
    /** NOAA records every event type only from 1996 on; earlier years hold tornadoes, hail and wind only. */
    public static final int STORM_FROM_YEAR = 1996;

    @Schema(description = "Internal ID of the property", example = "42")
    private Long propertyId;

    @Schema(description = "AVAILABLE when the parcel point could be evaluated against at least one source; "
            + "NO_COVERAGE when the property has no parcel polygon; UNAVAILABLE when the reference data could "
            + "not be read", example = STATUS_AVAILABLE)
    private String status;

    @Schema(description = "Unit of every distance in this section", example = DISTANCE_UNIT)
    private String distanceUnit;

    @Schema(description = "FEMA flood zone at the parcel point")
    private FloodZoneDTO flood;

    @Schema(description = "FEMA National Risk Index ratings of the census tract containing the parcel point")
    private HazardRatingsDTO hazards;

    @Schema(description = "NOAA storm events recorded near the parcel point")
    private StormHistoryDTO storms;

    @Schema(description = "Explanation when the section is empty or incomplete; null otherwise",
            example = "No parcel polygon for this property")
    private String note;

    @Schema(description = "Error text when the reference data could not be read; null otherwise")
    private String error;

    /** FEMA flood hazard zone at the parcel point. */
    @Data
    public static class FloodZoneDTO {
        @Schema(description = "AVAILABLE when a flood zone polygon contains the point; NO_DATA when the county is "
                + "loaded but no polygon contains the point; NO_COVERAGE when the county's flood map is not loaded",
                example = STATUS_AVAILABLE)
        private String status;
        @Schema(description = "FEMA zone code: A, AE, AH, AO, V, VE, X, D or OPEN WATER", example = "AE")
        private String zone;
        @Schema(description = "FEMA zone subtype, e.g. FLOODWAY or 0.2 PCT ANNUAL CHANCE FLOOD HAZARD; null when "
                + "FEMA gives none", example = "0.2 PCT ANNUAL CHANCE FLOOD HAZARD")
        private String zoneSubtype;
        @Schema(description = "True when the point is inside a Special Flood Hazard Area (1% annual chance); "
                + "null when there is no polygon", example = "true")
        private Boolean specialFloodHazardArea;
        @Schema(description = "Static base flood elevation in feet; null when FEMA gives none", example = "12.5")
        private Double baseFloodElevationFt;
        @Schema(description = "Flood depth in feet for AO zones; null when FEMA gives none", example = "2")
        private Double depthFt;
        @Schema(description = "FEMA study type of the polygon, e.g. SFHAs WITH HIGH FLOOD RISK or NP (no study)",
                example = "NP")
        private String studyType;
        @Schema(description = "FEMA map (DFIRM) identifier of the county", example = "12086C")
        private String firmId;
        @Schema(description = "Where the data comes from", example = SOURCE_FLOOD)
        private String source;
        @Schema(description = "Date the county flood map was downloaded from FEMA", example = "2026-11-14")
        private LocalDate dataAsOf;
        @Schema(description = "Explanation when data is missing; null otherwise")
        private String note;
    }

    /** FEMA National Risk Index ratings of one census tract. */
    @Data
    public static class HazardRatingsDTO {
        @Schema(description = "AVAILABLE when the tract has an NRI row; NO_DATA when the tract is known but has "
                + "no NRI row (water-only tracts); NO_COVERAGE when no census tract contains the point",
                example = STATUS_AVAILABLE)
        private String status;
        @Schema(description = "11-digit census tract FIPS code", example = "12086000107")
        private String tractFips;
        @Schema(description = "Census tract name", example = "Census Tract 1.07")
        private String tractName;
        @Schema(description = "NRI version", example = "December 2025")
        private String nriVersion;
        @Schema(description = "Composite risk score (0-100) over all 18 NRI hazards; null when FEMA gives none",
                example = "63")
        private Double overallScore;
        @Schema(description = "Composite rating: Very Low, Relatively Low, Relatively Moderate, Relatively High "
                + "or Very High; null means FEMA gives no rating (insufficient data), not low risk",
                example = "Relatively Moderate")
        private String overallRating;
        @Schema(description = "One rating per hazard, in a fixed order; a null rating means unknown")
        private List<HazardRatingDTO> ratings;
        @Schema(description = "Where the data comes from", example = SOURCE_HAZARDS)
        private String source;
        @Schema(description = "Date the NRI file was downloaded from FEMA", example = "2026-11-11")
        private LocalDate dataAsOf;
        @Schema(description = "Explanation when data is missing; null otherwise")
        private String note;
    }

    /** One NRI hazard of the tract. */
    @Data
    public static class HazardRatingDTO {
        @Schema(description = "NRI hazard code: HRCN, CFLD, IFLD, TRND, WFIR, SWND, LTNG or HWAV", example = "HRCN")
        private String code;
        @Schema(description = "Hazard name", example = "Hurricane")
        private String name;
        @Schema(description = "Risk score (0-100) of the hazard; null when FEMA gives none", example = "71.3")
        private Double score;
        @Schema(description = "Rating of the hazard; null means FEMA gives no rating for it", example = "Relatively High")
        private String rating;
    }

    /** NOAA storm events near the parcel point, counted by event type. */
    @Data
    public static class StormHistoryDTO {
        @Schema(description = "AVAILABLE when the county is in a loaded storm file; NO_DATA when it is loaded "
                + "but no event is recorded near the point; NO_COVERAGE when the property's state is not loaded",
                example = STATUS_AVAILABLE)
        private String status;
        @Schema(description = "Radius around the parcel point for events with a location", example = "10000")
        private Integer radius;
        @Schema(description = "First year counted", example = "1996")
        private Integer fromYear;
        @Schema(description = "Events with a location within the radius since fromYear", example = "57")
        private Integer eventsWithinRadius;
        @Schema(description = "Events recorded for the whole county since fromYear, located or not", example = "412")
        private Integer eventsInCounty;
        @Schema(description = "Date of the most recent event within the radius; null when none", example = "2024-10-09")
        private LocalDate lastEventDate;
        @Schema(description = "Counts per event type within the radius, most frequent first")
        private List<StormTypeCountDTO> byType;
        @Schema(description = "Where the data comes from", example = SOURCE_STORMS)
        private String source;
        @Schema(description = "Date the storm file was downloaded from NOAA", example = "2026-11-11")
        private LocalDate dataAsOf;
        @Schema(description = "Explanation when data is missing or partial; null otherwise")
        private String note;
    }

    /** Count of one NOAA event type near the point. */
    @Data
    public static class StormTypeCountDTO {
        @Schema(description = "NOAA event type", example = "Tornado")
        private String eventType;
        @Schema(description = "Events of this type within the radius since fromYear", example = "4")
        private Integer count;
        @Schema(description = "Date of the most recent event of this type", example = "2023-04-27")
        private LocalDate lastEventDate;
        @Schema(description = "Sum of the reported property damage in USD; null when NOAA gives none",
                example = "250000")
        private Long propertyDamageUsd;
    }
}
