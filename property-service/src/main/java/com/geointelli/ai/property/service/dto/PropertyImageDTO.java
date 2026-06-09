package com.geointelli.ai.property.service.dto;

import lombok.Data;

@Data
public class PropertyImageDTO {
    private Long id;

    private String imageUrl;

    private Integer imageOrder;

    private Boolean primaryImage;
}
