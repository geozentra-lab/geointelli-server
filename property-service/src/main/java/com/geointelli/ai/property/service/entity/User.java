package com.geointelli.ai.property.service.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_user_id", unique = true, nullable = false)
    private String keycloakUserId;

    private String username;

    private String email;

    private String firstName;

    private String lastName;

    /** Legacy column, no longer read: the account type is the user's Keycloak realm role (SCRUM-204). */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private UserPersona persona = UserPersona.USER;

    /** When the user answered the onboarding page (SCRUM-204); null until then. */
    @Column(name = "onboarding_completed_at")
    private java.time.Instant onboardingCompletedAt;
    @OneToMany(
        mappedBy = "user",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<Favorite> favorites = new ArrayList<>();
}
