package com.geointelli.ai.property.service.config;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String SCHOOL_ASSIGN_PATH = "/api/property-school-analysis/assign";
    static final String SCHOOL_CALCULATE_ALL_PATH = "/api/property-school-analysis/calculate-all";

    /**
     * Whether a calculate-all request names a score version other than exactly "1" (any value, also an invalid one,
     * so a spelling the controller might still read as 2 is never let through without the admin role).
     */
    static boolean requestsScoreVersionOtherThanOne(HttpServletRequest request) {
        String[] versions = request.getParameterValues("version");
        return versions != null && Arrays.stream(versions).anyMatch(version -> !"1".equals(version));
    }

    @Bean
    SecurityFilterChain resourceServerSecurityFilterChain(
        HttpSecurity http,
        Converter<Jwt, AbstractAuthenticationToken> authenticationConverter) throws Exception {
        http.oauth2ResourceServer(resourceServer -> {
            resourceServer.jwt(jwtDecoder -> {
            jwtDecoder.jwtAuthenticationConverter(authenticationConverter);
            });
        });

        http.sessionManagement(sessions -> {
            sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        }).csrf(csrf -> {
            csrf.disable();
        });

        http.authorizeHttpRequests(requests -> {
            // requests.requestMatchers("/api/properties/**", "/api/users/**").hasRole("USER");
            // Market evidence (SCRUM-131) is licensed provider data: any signed-in user may read it, not the public
            requests.requestMatchers("/api/market-evidence/**").authenticated();
            // Admin endpoints need the Keycloak realm role "admin" (ROLE_admin): 401 without a token, 403 without the role
            requests.requestMatchers("/api/admin/**").hasRole("admin");
            // School assignment and school score v2 jobs (SCRUM-203) rewrite the school data of whole counties;
            // calculate-all without a version (or version=1) keeps the open v1 behaviour
            requests.requestMatchers(HttpMethod.POST, SCHOOL_ASSIGN_PATH).hasRole("admin");
            requests.requestMatchers(new AndRequestMatcher(
                AntPathRequestMatcher.antMatcher(HttpMethod.POST, SCHOOL_CALCULATE_ALL_PATH),
                SecurityConfig::requestsScoreVersionOtherThanOne)).hasRole("admin");
            requests.anyRequest().permitAll();
        });

        return http.build();
    }

    @Bean
    JwtAuthenticationConverter authenticationConverter(AuthoritiesConverter authoritiesConverter) {
        var authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            return authoritiesConverter.convert(jwt.getClaims());
        });
        return authenticationConverter;
    }
}