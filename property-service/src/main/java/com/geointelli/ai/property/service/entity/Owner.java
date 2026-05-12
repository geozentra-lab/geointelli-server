package com.geointelli.ai.property.service.entity;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "owners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "property")
public class Owner {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String description;

    private String role;

    private Integer percentageOwn;

    private String shortDescription;

    private String tenancyCd;

    private Boolean marriedFlag;

    private String message;

    @ManyToMany(mappedBy = "owners")
    private List<Property> properties;
}