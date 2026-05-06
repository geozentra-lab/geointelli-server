package com.geointelli.ai.property.service.entity;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@ToString
@Table(name = "taxes")
@NoArgsConstructor
@AllArgsConstructor
public class Tax {

    @Id
    @GeneratedValue
    private Long id;

    private BigDecimal cityExemptionValue;

    private BigDecimal cityTaxableValue;

    private BigDecimal countyExemptionValue;

    private BigDecimal countyTaxableValue;

    private BigDecimal regionalExemptionValue;

    private BigDecimal regionalTaxableValue;

    private BigDecimal schoolExemptionValue;

    private BigDecimal schoolTaxableValue;

    private Integer year;

    private String message;

    @ManyToOne
    @JoinColumn(name = "property_id")
    private Property property;
}