package com.geointelli.ai.property.service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "property_import_mapping",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_property_import_mapping",
            columnNames = {
                "source_county",
                "source_state",
                "source_property_id"
            }
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertyImportMapping extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_county", nullable = false)
    private String sourceCounty;

    @Column(name = "source_state", nullable = false)
    private String sourceState;

    @Column(name = "source_property_id", nullable = false)
    private Long sourcePropertyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "geozentra_property_id", nullable = false)
    private Property geozentraProperty;
}