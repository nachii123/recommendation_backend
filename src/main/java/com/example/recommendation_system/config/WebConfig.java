package com.example.recommendation_system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> originPatterns = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .map(origin -> "*".equals(origin) ? "*" : origin)
                .collect(Collectors.toList());

        registry.addMapping("/api/**")
                .allowedOriginPatterns(originPatterns.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
