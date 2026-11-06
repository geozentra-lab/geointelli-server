package com.geointelli.ai.property.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Independent, opt-in contact-request delivery; saved-search monitoring need not be enabled. */
@Component
@ConfigurationProperties(prefix = "contact-requests")
@Validated
@Getter
@Setter
public class ContactRequestProperties {
    private boolean publisherEnabled;
    @Min(1) @Max(100) private int batchSize = 20;
    @Min(1) @Max(100) private int maxAttempts = 10;
    @Min(1) @Max(30) private int confirmTimeoutSeconds = 10;
    @Min(1) @Max(3600) private int retryBaseSeconds = 30;
    @NotBlank private String exchange = "geozentra.alerts";
    @NotBlank private String queue = "geozentra.notifications.alerts";
    @NotBlank private String routingKey = "contacts.v1";
}

