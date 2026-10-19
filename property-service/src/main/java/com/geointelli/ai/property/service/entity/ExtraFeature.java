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
@Table(name = "extra_features")
@Getter
@Setter
@ToString(exclude = "property")
public class ExtraFeature extends AuditableEntity {

    @Id
    @GeneratedValue
    private Long id;

    private Integer actualYearBuilt;

    private Double adjustedUnitPrice;

    private BigDecimal depreciatedValue;

    private String description;

    private String message;

    private Double percentCondition;

    private Integer rollYear;

    private Integer units;

    private String useCode;

    @ManyToOne
    private Property property;
}
