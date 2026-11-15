package com.geointelli.ai.property.service.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A named analysis scenario owned by one user (SCRUM-147). It stores only the user-supplied assumptions,
 * never calculation results and never property facts: the optional property is a link, not a copy.
 */
@Entity
@Table(name = "scenarios")
@Getter
@Setter
public class Scenario extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private Property property;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", nullable = false, length = 30)
    private ScenarioType scenarioType;

    @Column(name = "input_version", nullable = false)
    private int inputVersion = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode assumptions;
}
