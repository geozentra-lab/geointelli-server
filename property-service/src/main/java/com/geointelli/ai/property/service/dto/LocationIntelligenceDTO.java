package com.geointelli.ai.property.service.dto;

import java.util.ArrayList;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Location intelligence of a property (SCRUM-128 / SCRUM-53): the school, transport and risk sections in one
 * response, each exactly as its own endpoint returns it. A section that cannot be read is null and listed in
 * {@code errors}; a section that reports its own UNAVAILABLE status is kept and listed in {@code errors} too.
 */
@Data
public class LocationIntelligenceDTO {

    public static final String STATUS_COMPLETE = "COMPLETE";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    public static final String SECTION_SCHOOLS = "schools";
    public static final String SECTION_TRANSPORT = "transport";
    public static final String SECTION_RISK = "risk";

    /** The section could not be read at all; it is null in the response. */
    public static final String ERROR_SECTION_FAILED = "SECTION_FAILED";
    /** The section was read but its data source failed; the section is returned with status UNAVAILABLE. */
    public static final String ERROR_SECTION_UNAVAILABLE = "SECTION_UNAVAILABLE";

    @Schema(description = "Internal ID of the property", example = "42")
    private Long propertyId;

    @Schema(description = "COMPLETE when every section was read (a section may still report NO_COVERAGE or "
            + "NO_NEARBY_RESULTS: that is an answer, not an error); PARTIAL when at least one section is in errors; "
            + "UNAVAILABLE when no section could be read", example = STATUS_COMPLETE)
    private String status;

    @Schema(description = "School section, as GET /api/properties/id/{id}/location-intelligence/schools; null when "
            + "it could not be read")
    private SchoolContextDTO schools;

    @Schema(description = "Transport section, as GET /api/properties/id/{id}/location-intelligence/transport; null "
            + "when it could not be read")
    private TransportContextDTO transport;

    @Schema(description = "Risk section, as GET /api/properties/id/{id}/location-intelligence/risk; null when it "
            + "could not be read")
    private RiskContextDTO risk;

    @Schema(description = "One entry per section that failed, in the order schools, transport, risk; empty when "
            + "status is COMPLETE")
    private List<SectionErrorDTO> errors = new ArrayList<>();

    /** Error of one section, without database or provider internals. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionErrorDTO {

        @Schema(description = "schools, transport or risk", example = SECTION_TRANSPORT)
        private String section;

        @Schema(description = "SECTION_FAILED: the section could not be read and is null; SECTION_UNAVAILABLE: the "
                + "section is returned with status UNAVAILABLE because its data source failed",
                example = ERROR_SECTION_UNAVAILABLE)
        private String code;

        @Schema(description = "Short explanation for the caller", example = "Transport data source unavailable")
        private String message;
    }
}
