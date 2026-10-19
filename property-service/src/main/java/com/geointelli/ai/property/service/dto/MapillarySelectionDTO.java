package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class MapillarySelectionDTO {
    private byte[] content;
    private String extension;
    private String sourceImageId;
    private String sourceUrl;
    private String contributor;
    private Instant capturedAt;
    private String processingNote;
}
