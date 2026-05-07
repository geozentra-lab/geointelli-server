package com.geointelli.ai.property.service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class Address {

    @Id
    @GeneratedValue
    private Long id;

    private String address;

    private int buildingNumber;

    private String city;

    private String houseNumberSuffix;

    private String message;

    private String streetName;

    private int streetNumber;

    private String streetPrefix;

    private String streetSuffix;

    private String streetSuffixDirection;

    private String unit;

    private String zip;

    @OneToOne
    private Property property;
}
