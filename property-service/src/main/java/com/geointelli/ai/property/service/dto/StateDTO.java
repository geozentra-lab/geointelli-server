package com.geointelli.ai.property.service.dto;

import lombok.Data;

/** Lists a state available for county selection. */
@Data
public class StateDTO {
    private Long id;
    private String code;
    private String name;
}
