package com.geointelli.ai.property.service.entity;

import org.locationtech.jts.geom.MultiPolygon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "parcels")
@Getter
@Setter
@ToString(exclude = "property")
@NoArgsConstructor
@AllArgsConstructor
public class Parcel extends AuditableEntity {

    @Id
    @Column(name = "fid")
    private Long id;

    @Column(name = "folio")
    private String folio;

    @Column(name = "geom", columnDefinition = "geometry(MultiPolygon,4326)")
    private MultiPolygon geom;

    @Column(name = "lot_size")
    private Double lotSize;

    @ManyToOne
    @JoinColumn(name = "property_id")
    private Property property;
}
