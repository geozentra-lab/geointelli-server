package com.geointelli.ai.property.service.dto;

import com.geointelli.ai.property.service.enums.ImportJobType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** Bounded list of tracked import jobs, plus the import types that run without a job record. */
@Data
public class ImportJobPageDTO {
    @Schema(description = "Bounded page of tracked jobs, newest first")
    private PageResponse<ImportJobDTO> jobs;
    @Schema(description = "Import types that run without a job record and cannot be listed", example = "[\"PROPERTY_IMAGE_CSV_IMPORT\"]")
    private List<ImportJobType> untrackedJobTypes;
    @Schema(description = "Explanation of what is and is not tracked")
    private String note;
}
