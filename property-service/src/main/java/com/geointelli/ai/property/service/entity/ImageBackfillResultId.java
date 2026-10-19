package com.geointelli.ai.property.service.entity;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** Matches the existing combined primary key: job ID and property ID. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ImageBackfillResultId implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long job;
    private Long property;
}
