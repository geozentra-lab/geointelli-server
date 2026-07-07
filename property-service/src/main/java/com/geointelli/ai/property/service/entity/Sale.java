package com.geointelli.ai.property.service.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

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
@ToString(exclude = "property")
@Table(name = "sales")
@NoArgsConstructor
@AllArgsConstructor
public class Sale extends AuditableEntity {

    @Id
    @GeneratedValue
    private Long id;

    private LocalDate saleDate;

    private BigDecimal salePrice;

    private String saleType;

    private String instrumentNumber;

    @ManyToOne
    @JoinColumn(name = "property_id")
    private Property property;
}
