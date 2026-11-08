package com.geointelli.ai.property.service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Browser access for the web client.
 *
 * <p>No {@code @EnableWebMvc} here: it switches off Spring Boot's MVC auto-configuration, so the
 * HTTP converters ignored {@link JacksonConfig} and wrote LocalDate as {@code [2026,11,8]} and
 * Instant as epoch seconds instead of ISO-8601 strings.
 *
 * <p>PATCH is allowed because the client updates profiles, alerts, saved searches, portfolios,
 * CRM records and lender applications with it; without it the browser preflight fails with 403.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins:http://localhost:4200}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location", "Content-Disposition")
                .allowCredentials(true);
    }
}
