package com.geointelli.ai.property.service.dto;

import com.geointelli.ai.property.service.enums.DataQualityCategory;
import com.geointelli.ai.property.service.enums.DataQualityRule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** One data-quality issue with a reproducible reference and the provenance of the property (SCRUM-189). */
@Data
public class DataQualityIssueDTO {
    @Schema(description = "Reproducible issue reference RULE:entityType:entityId", example = "SALE_DUPLICATE:SALE:9012")
    private String reference;
    @Schema(description = "Rule that found the issue", example = "SALE_DUPLICATE")
    private DataQualityRule rule;
    @Schema(description = "Kind of issue", example = "DUPLICATE")
    private DataQualityCategory category;
    @Schema(description = "Entity the issue points to: PROPERTY, SALE or ASSESSMENT", example = "SALE")
    private String entityType;
    @Schema(description = "ID of that entity", example = "9012")
    private Long entityId;
    @Schema(description = "Property concerned by the issue", example = "345")
    private Long propertyId;
    @Schema(description = "County folio of the property", example = "3049340031160")
    private String folio;
    @Schema(description = "Human-readable explanation of the issue", example = "Duplicate of sale 8800")
    private String detail;
    @Schema(description = "Source county from property_import_mapping; null when the property was not imported from a source file", example = "Miami-Dade")
    private String sourceCounty;
    @Schema(description = "Source state from property_import_mapping; null when unknown", example = "FL")
    private String sourceState;
    @Schema(description = "Property ID in the source file; null when unknown", example = "123456")
    private Long sourcePropertyId;
}
