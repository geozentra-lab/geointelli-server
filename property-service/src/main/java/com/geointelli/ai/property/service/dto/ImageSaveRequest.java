package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import lombok.Data;

/** Contains image bytes and optional source information for the shared saving flow. */
@Data
public class ImageSaveRequest {
    private byte[] content;
    private String extension;
    private String originalUrl;
    private String source;
    private String sourceImageId;
    private String contributor;
    private String license;
    private Instant capturedAt;
    private String matchStatus;
    private String processingNote;
}
