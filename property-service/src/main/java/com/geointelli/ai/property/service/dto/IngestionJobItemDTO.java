package com.geointelli.ai.property.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One failed or skipped record of an ingestion job. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngestionJobItemDTO {
    @Schema(description = "CSV row as file:row, or the folio for API ingestion", example = "addresses.csv:1234")
    private String itemKey;
    @Schema(description = "FAILED or SKIPPED", example = "FAILED")
    private String status;
    @Schema(description = "Reason code", example = "INVALID_ROW")
    private String reason;
    @Schema(description = "Short explanation without server paths or credentials")
    private String message;
}
