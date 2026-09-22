package com.geointelli.ai.property.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.geointelli.ai.property.service.client.MiamiDadeApiClient;

@Configuration
public class ApiClientConfig {
    @Bean
    public MiamiDadeApiClient MiamiDadeApiClient() {
        return new MiamiDadeApiClient();
    }
}
