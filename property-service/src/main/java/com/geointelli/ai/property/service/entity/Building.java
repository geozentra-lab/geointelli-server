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
@Table(name = "buildings")
@Getter
@Setter
@ToString
public class Building {

    @Id
    @GeneratedValue
    private Long id;

    private Integer actual;

    private Integer actualArea;

    private Double adjustedBasePrice;

    private Integer buildingNo;

    private BigDecimal depreciatedValue;

    private Integer effective;

    private Integer effectiveArea;

    private Integer grossArea;

    private Integer heatedArea;

    private String message;

    private Double percentComp;

    private Double percentageGood;

    private BigDecimal replacementCostNew;

    private Integer rollYear;

    private Integer segNo;

    private Integer totalAdjustedPoints;

    private String traversePoints;

    private String improvementModelDesc;

    @ManyToOne
    private Property property;
}