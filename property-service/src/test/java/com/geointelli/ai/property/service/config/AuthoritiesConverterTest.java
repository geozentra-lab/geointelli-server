package com.geointelli.ai.property.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AuthoritiesConverterTest {
    private final AuthoritiesConverter converter = new AuthoritiesConverter();

    @Test
    void onlyExactRealmRolesBecomeAuthorities() {
        var result = converter.convert(Map.of("realm_access", Map.of("roles", List.of("admin", "REALTOR", "realtor")),
                "persona", "BANK_LENDER", "roles", List.of("lender")));
        assertThat(result).extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_admin", "ROLE_REALTOR", "ROLE_realtor");
    }

    @Test
    void malformedClaimsGrantNothingRatherThanThrowing() {
        for (var claims : List.of(Map.<String, Object>of(), Map.<String, Object>of("realm_access", "admin"),
                Map.<String, Object>of("realm_access", Map.of("roles", "admin")))) {
            assertThat(converter.convert(claims)).isEmpty();
        }
        assertThat(converter.convert(Map.of("realm_access", Map.of("roles", List.of(7, "admin", "")))))
                .extracting(authority -> authority.getAuthority()).containsExactly("ROLE_admin");
    }
}
