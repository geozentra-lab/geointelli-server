package com.geointelli.ai.property.service.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import lombok.Getter;
import lombok.Setter;

/** Stores one set of research preferences per local user. */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
public class UserPreferences extends AuditableEntity {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(precision = 15, scale = 2)
    private BigDecimal minBudget;

    @Column(precision = 15, scale = 2)
    private BigDecimal maxBudget;

    @ManyToMany
    @JoinTable(name = "user_target_counties", joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "county_id"))
    @OrderColumn(name = "position")
    private List<County> targetCounties = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "user_investment_goals", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "position")
    @Column(name = "goal", nullable = false, length = 200)
    private List<String> investmentGoals = new ArrayList<>();
}
