package com.geointelli.ai.notification.service.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** SES configuration is optional until the background sender is enabled. */
@Component
@ConfigurationProperties(prefix = "notifications.email")
@Validated
@Getter
@Setter
public class NotificationEmailProperties {
    private boolean enabled;
    @Email
    private String fromAddress = "";
    @NotBlank
    private String region = "us-east-1";
    @NotBlank
    private String inboxUrl = "http://localhost:4200/notifications";

    @AssertTrue(message = "Email inbox URL must be an absolute HTTP or HTTPS URL")
    public boolean isInboxUrlValid() {
        try {
            java.net.URI uri = java.net.URI.create(inboxUrl);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException | NullPointerException error) {
            return false;
        }
    }
    @Min(1000)
    @Max(3600000)
    private long pollDelayMillis = 30000;
    @Min(1)
    @Max(100)
    private int stepsPerTick = 10;
    @Min(1)
    @Max(1000)
    private int maxDigestItems = 100;
    @Min(1)
    @Max(10)
    private int maxAttempts = 3;
    @Min(1)
    @Max(30)
    private int timeoutSeconds = 10;
    @Min(60)
    @Max(3600)
    private int leaseSeconds = 120;
    @Min(1)
    @Max(3600)
    private int retryDelaySeconds = 60;

    @AssertTrue(message = "Enabling email requires a verified SES sender address")
    public boolean isSenderConfigured() {
        return !enabled || (fromAddress != null && !fromAddress.isBlank());
    }

    @AssertTrue(message = "Email lease must exceed twice the provider timeout plus 30 seconds")
    public boolean isLeaseValid() {
        return leaseSeconds > 2 * timeoutSeconds + 30;
    }
}
