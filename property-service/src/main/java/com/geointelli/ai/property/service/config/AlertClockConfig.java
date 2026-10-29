package com.geointelli.ai.property.service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Uses UTC for cadence and retry calculations and allows deterministic tests. */
@Configuration
public class AlertClockConfig {
    @Bean("alertClock")
    public Clock alertClock() { return Clock.systemUTC(); }
}
