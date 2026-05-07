package com.geointelli.ai.property.service.entity;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "assessments")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Assessment {

    @Id
    @GeneratedValue
    private Long id;

    private BigDecimal assessedValue;

    private BigDecimal buildingOnlyValue;

    private BigDecimal extraFeatureValue;

    private BigDecimal landValue;

    private BigDecimal totalValue;

    private Integer year;

    private String message;

    @ManyToOne
    private Property property;
}
