package com.geointelli.ai.notification.service.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Bounded processing settings and topology shared with the independently
 * deployed producer.
 */
@Component
@ConfigurationProperties(prefix = "notifications")
@Validated
@Getter
@Setter
public class NotificationProperties {
    @NotBlank
    private String exchange = "geozentra.alerts";
    @NotBlank
    private String queue = "geozentra.notifications.alerts";
    @NotBlank
    private String routingKey = "alerts.v1";
    @NotBlank
    private String deadLetterExchange = "geozentra.notifications.dead";
    @NotBlank
    private String deadLetterQueue = "geozentra.notifications.alerts.dlq";
    @NotBlank
    private String deadLetterRoutingKey = "alerts.failed.v1";
    @Min(1)
    @Max(10)
    private int maxAttempts = 3;
    @Min(1)
    @Max(30000)
    private long retryInitialMillis = 1000;
    @Min(1)
    @Max(60000)
    private long retryMaxMillis = 10000;
    @Min(1)
    @Max(30)
    private int confirmTimeoutSeconds = 10;
    @Min(1024)
    @Max(10485760)
    private int maxBodyBytes = 262144;

    @AssertTrue(message = "Retry maximum must be at least the initial interval")
    public boolean isRetryRangeValid() {
        return retryMaxMillis >= retryInitialMillis;
    }

    @AssertTrue(message = "Main and dead-letter destinations must have different names")
    public boolean isTopologyValid() {
        return !exchange.equals(deadLetterExchange) && !queue.equals(deadLetterQueue);
    }
}
