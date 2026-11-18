package com.geointelli.ai.property.service.config;

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
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import jakarta.servlet.DispatcherType;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * SCRUM-94 route matrix. Valid JWTs inherit baseline user access. Realm roles
 * grant
 * investment, realtor CRM, lender and admin features; private services retain
 * their ownership checks. Subscription restrictions are not configured.
 */
@Configuration
@EnableWebSecurity
@OpenAPIDefinition(security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SecurityConfig {

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

        // Uses the existing CorsConfig, including PATCH preflights, before
        // authentication.
        http.cors(Customizer.withDefaults());

        http.authorizeHttpRequests(requests -> {
            // Preserve the original status on internal servlet error dispatches.
            requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
            requests.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                    "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll();

            // Operational routes must precede the general property-read rules.
            requests.requestMatchers("/api/admin/**", "/api/ingestion/**", "/api/images/import/**",
                    "/api/properties/images/backfill/**").hasRole(ApplicationRoles.ADMIN);
            requests.requestMatchers(HttpMethod.POST, "/api/predictions/bulk",
                    "/api/property-school-analysis/calculate-all",
                    "/api/property-school-analysis/*/calculate-property-score",
                    "/api/property-transportation-analysis/calculate-all",
                    "/api/property-transportation-analysis/*/calculate-property-score")
                    .hasRole(ApplicationRoles.ADMIN);

            requests.requestMatchers("/api/clients/**", "/api/leads/**", "/api/tasks/**", "/api/transactions/**",
                    "/api/crm/**", "/api/dashboards/realtor/**")
                    .hasAnyRole(ApplicationRoles.REALTOR, ApplicationRoles.ADMIN);
            requests.requestMatchers("/api/lender/**", "/api/dashboards/lender/**")
                    .hasAnyRole(ApplicationRoles.LENDER, ApplicationRoles.ADMIN);
            requests.requestMatchers("/api/portfolios/**", "/api/scenarios/**", "/api/dashboards/investor/**",
                    "/api/opportunities", "/api/properties/id/*/opportunity-score", "/api/calculations/investment")
                    .hasAnyRole(ApplicationRoles.INVESTOR, ApplicationRoles.ADMIN);

            // Every authenticated persona inherits normal-user features; no explicit user
            // role is needed.
            requests.requestMatchers("/api/users/me/**", "/api/saved-searches/**", "/api/alerts/**",
                    "/api/contact-requests/**", "/api/recommendations/**", "/api/dashboards/user/**")
                    .authenticated();
            requests.requestMatchers(HttpMethod.GET, "/api/properties/**", "/api/addresses/**", "/api/parcels/**",
                    "/api/locations/**", "/api/map/**", "/api/images/property/*", "/api/predictions/bypropertyid")
                    .authenticated();
            requests.requestMatchers(HttpMethod.POST, "/api/properties/*/favorite", "/api/properties/compare",
                    "/api/calculations/mortgage", "/api/calculations/affordability",
                    "/api/property-school-analysis/bulk", "/api/property-transportation-analysis/bulk")
                    .authenticated();
            requests.requestMatchers(HttpMethod.DELETE, "/api/properties/*/favorite").authenticated();
            requests.anyRequest().denyAll();
        });

        return http.build();
    }

    @Bean
    JwtAuthenticationConverter authenticationConverter(AuthoritiesConverter authoritiesConverter) {
        var authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            if (jwt.getSubject() == null || jwt.getSubject().isBlank())
                throw new InvalidBearerTokenException("The bearer token must identify a user");
            return authoritiesConverter.convert(jwt.getClaims());
        });
        return authenticationConverter;
    }
}
