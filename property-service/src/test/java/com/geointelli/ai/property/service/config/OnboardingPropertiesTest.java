package com.geointelli.ai.property.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** Binding of onboarding.self-service-roles (SCRUM-204): every role waits for an admin unless it is listed. */
class OnboardingPropertiesTest {

    private static OnboardingProperties bind(Map<String, String> properties) {
        OnboardingProperties bound = new OnboardingProperties();
        new Binder(new MapConfigurationPropertySource(properties)).bind("onboarding", Bindable.ofInstance(bound));
        bound.validate();
        return bound;
    }

    @Test
    @DisplayName("Unset or empty (the application.yml default): no self-service role, admin approval for both")
    void adminApprovalByDefault() {
        for (OnboardingProperties properties : new OnboardingProperties[] {
            bind(Map.of()), bind(Map.of("onboarding.self-service-roles", ""))}) {
            assertThat(properties.getSelfServiceRoles()).isEmpty();
            assertThat(properties.isSelfService(ApplicationRoles.INVESTOR)).isFalse();
            assertThat(properties.isSelfService(ApplicationRoles.LENDER)).isFalse();
        }
    }

    @Test
    @DisplayName("A comma-separated list turns instant grants back on, normalised")
    void listedRolesAreSelfService() {
        OnboardingProperties properties = bind(Map.of("onboarding.self-service-roles", " Investor ,lender"));

        assertThat(properties.getSelfServiceRoles()).containsExactly("investor", "lender");
        assertThat(properties.isSelfService(ApplicationRoles.LENDER)).isTrue();
    }
}
