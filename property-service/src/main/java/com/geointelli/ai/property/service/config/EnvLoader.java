package com.geointelli.ai.property.service.config;

import org.springframework.stereotype.Component;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;

@Component
public class EnvLoader {
    @PostConstruct
    public void loadEnv() {
        Dotenv dotenv = Dotenv.load();

        System.setProperty("DB_USER", dotenv.get("DB_USER"));
        System.setProperty("DB_PASSWORD", dotenv.get("DB_PASSWORD"));
    }
}
