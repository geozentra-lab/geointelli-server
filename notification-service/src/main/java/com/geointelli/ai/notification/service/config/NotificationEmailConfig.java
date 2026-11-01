package com.geointelli.ai.notification.service.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * Uses the standard AWS credential chain; credentials are never stored in
 * notification preferences.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "notifications.email", name = "enabled", havingValue = "true")
public class NotificationEmailConfig {
    @Bean
    @Lazy
    public SesV2Client sesV2Client(NotificationEmailProperties settings) {
        return SesV2Client.builder().region(Region.of(settings.getRegion()))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
                        .apiCallAttemptTimeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
                        .retryPolicy(RetryPolicy.none()).build())
                .build();
    }
}
