package com.geointelli.ai.property.service.config;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/** Report boundaries use UTC independently of the host timezone. */
@Configuration
public class PortfolioSummaryClockConfig {
    @Bean("portfolioSummaryClock")
    public Clock portfolioSummaryClock() { return Clock.systemUTC(); }
}
