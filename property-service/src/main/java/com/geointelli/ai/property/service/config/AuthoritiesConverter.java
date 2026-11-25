package com.geointelli.ai.property.service.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Maps only signed Keycloak realm roles; malformed role claims grant no
 * authorities.
 */
@Component
public class AuthoritiesConverter implements
        Converter<Map<String, Object>, Collection<GrantedAuthority>> {

    /**
     * Returns realm-role authorities without trusting persona or client-role
     * claims.
     */
    @Override
    public Collection<GrantedAuthority> convert(Map<String, Object> claims) {
        if (!(claims.get("realm_access") instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get("roles") instanceof Collection<?> roles))
            return List.of();
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (Object value : roles) {
            if (value instanceof String role && !role.isBlank())
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return List.copyOf(authorities);
    }
}
