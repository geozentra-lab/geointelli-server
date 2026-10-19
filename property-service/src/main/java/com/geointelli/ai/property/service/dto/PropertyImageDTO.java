package com.geointelli.ai.property.service.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PropertyImageDTO {
    private Long id;

    private String storagePath;

    private Integer displayOrder;

    private Boolean primaryImage;

    private String source;
    private String sourceImageId;
    private String sourceUrl;
    private String contributor;
    private String license;
    private Instant capturedAt;
    private String matchStatus;
}
