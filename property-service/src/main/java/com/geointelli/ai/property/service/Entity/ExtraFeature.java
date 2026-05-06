package com.geointelli.ai.property.service.entity;

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
@ToString
public class ExtraFeature {

    @Id
    @GeneratedValue
    private Long id;

    private String featureType;

    private Double area;

    private Double value;

    @ManyToOne
    private Property property;
}
