package com.geointelli.ai.property.service.entity;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "lands")
@Getter
@Setter
@ToString(exclude = "property")
public class Land {

    @Id
    @GeneratedValue
    private Long id;

    private Double adjustedUnitPrice;

    private BigDecimal calculatedValue;

    private Double depth;

    private Double frontFeet;

    private String landUse;

    private String landlineType;

    private String message;

    private String muniZone;

    private String muniZoneDescription;

    private String paZoneDescription;

    private Double percentCondition;

    private Integer rollYear;

    private Integer totalAdjustments;

    private String unitType;

    private Double units;

    private String useCode;

    private String zone;

    @ManyToOne
    private Property property;
}
