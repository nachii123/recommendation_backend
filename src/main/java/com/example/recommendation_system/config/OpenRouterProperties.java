package com.example.recommendation_system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openrouter")
public record OpenRouterProperties(
        String apiKey,
        String baseUrl,
        String defaultModel
) {
}
