package com.geointelli.ai.property.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PropertyImageDTO {
    private Long id;

    private String storagePath;

    private Integer displayOrder;

    private Boolean primaryImage;
}
