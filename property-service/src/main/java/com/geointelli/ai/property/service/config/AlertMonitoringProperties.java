package com.geointelli.ai.property.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Bounded background work and opt-in delivery settings, configured through application.yml/.env. */
@Component
@ConfigurationProperties(prefix = "alerts")
@Validated
@Getter
@Setter
public class AlertMonitoringProperties {
    private boolean enabled;
    @Min(1000) @Max(3600000)
    private long pollDelayMillis = 30000;
    @Min(1) @Max(1000)
    private int idBatchSize = 500;
    @Min(1) @Max(100)
    private int stepsPerTick = 10;
    @Min(1) @Max(86400)
    private int retryDelaySeconds = 300;
    @Min(1) @Max(10080)
    private int maxScanAgeMinutes = 1440;
    @Valid
    private Publisher publisher = new Publisher();

    @Getter
    @Setter
    public static class Publisher {
        private boolean enabled;
        @Min(1000) @Max(3600000)
        private long pollDelayMillis = 5000;
        @Min(1) @Max(100)
        private int batchSize = 20;
        @Min(1) @Max(100)
        private int maxAttempts = 10;
        @Min(1) @Max(30)
        private int confirmTimeoutSeconds = 10;
        @Min(1) @Max(3600)
        private int retryBaseSeconds = 30;
        @NotBlank
        private String exchange = "geozentra.alerts";
        @NotBlank
        private String queue = "geozentra.notifications.alerts";
        @NotBlank
        private String routingKey = "alerts.v1";
    }
}
