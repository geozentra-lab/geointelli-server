package com.geointelli.ai.property.service.config;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class AuthoritiesConverter implements
        Converter<Map<String, Object>, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Map<String, Object> claims) {

        Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");

        if (realmAccess == null || realmAccess.get("roles") == null) {
            return Collections.emptyList();
        }

        Collection<String> roles = (Collection<String>) realmAccess.get("roles");

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
