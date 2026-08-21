package com.example.recommendation_system.dto;

public record ChatRequest(
        String sessionId,
        String message
) {
}
