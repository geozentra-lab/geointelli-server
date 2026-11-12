package com.geointelli.ai.property.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** UTC clock of the lender dashboard, replaceable in tests. */
@Configuration
public class LenderDashboardClockConfig {
    @Bean("lenderDashboardClock")
    public Clock lenderDashboardClock() { return Clock.systemUTC(); }
}
