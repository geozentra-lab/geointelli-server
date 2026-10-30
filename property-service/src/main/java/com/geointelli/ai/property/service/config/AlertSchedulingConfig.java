package com.geointelli.ai.property.service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Does not start scheduled monitoring until the schema is migrated and alerts.enabled is true. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "alerts", name = "enabled", havingValue = "true")
public class AlertSchedulingConfig {
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("alert-scheduler-");
        return scheduler;
    }
}
