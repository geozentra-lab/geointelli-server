package com.geointelli.ai.property.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GeometryDTO {

    private String type;

    private Object coordinates;
}
