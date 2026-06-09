package com.geointelli.ai.property.service.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "properties")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Property {

    @Id
    @GeneratedValue
    private Long id;

    @Column(unique = true)
    private String folio;

    private String parentFolio;

    private Integer bathroomCount;

    private Integer bedroomCount;

    private Double halfBathroomCount;

    private Integer buildingActualArea;

    private Integer buildingBaseArea;

    private Integer buildingEffectiveArea;

    private Integer buildingGrossArea;

    private Integer buildingHeatedArea;

    private String dorCode;

    private String dorDescription;

    private Integer neighborhood;

    private String neighborhoodDescription;

    private Double lotSize;

    private Integer floorCount;

    private Integer unitCount;

    private String yearBuilt;

    private String municipality;

    private String subdivision;

    private String primaryZone;

    private String primaryZoneDescription;

    private String status;

    private String showCurrentValuesFlag;

    private String message;

    @ManyToMany
    @JoinTable(
        name = "property_owner",
        joinColumns = @JoinColumn(name = "property_id"),
        inverseJoinColumns = @JoinColumn(name = "owner_id")
    )
    private List<Owner> owners;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Assessment> assessments = new ArrayList<>();

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Building> buildings = new ArrayList<>();

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Land> lands = new ArrayList<>();

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Sale> sales = new ArrayList<>();

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Tax> taxes = new ArrayList<>();

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Parcel> parcels = new ArrayList<>();

    @OneToOne(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    private Address address;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PropertyImage> images = new ArrayList<>();

    public void addParcel(Parcel parcel) {
    if (this.parcels == null || isImmutable(this.parcels)) {
        this.parcels = new ArrayList<>();
    }
    this.parcels.add(parcel);
    parcel.setProperty(this);
}

    public void setParcels(List<Parcel> parcels) {
        if (parcels == null) {
            this.parcels = new ArrayList<>();
        } else {
            this.parcels = new ArrayList<>(parcels);
        }
    }

    private boolean isImmutable(List<?> list) {
        return list.getClass().getName().contains("Immutable") || 
            list.getClass().getName().contains("Arrays$ArrayList");
    }
}