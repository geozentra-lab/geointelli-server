package com.geointelli.ai.property.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImageBackfillResultDTO {
    private long propertyId;
    private String status;
    private String reason;
    private Long imageId;
}
