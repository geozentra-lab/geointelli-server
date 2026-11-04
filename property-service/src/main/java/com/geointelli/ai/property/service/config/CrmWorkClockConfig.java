package com.geointelli.ai.property.service.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** All task deadlines, completion times and pipeline observation times use UTC. */
@Configuration
public class CrmWorkClockConfig {
    @Bean("crmWorkClock")
    public Clock crmWorkClock() { return Clock.systemUTC(); }
}
