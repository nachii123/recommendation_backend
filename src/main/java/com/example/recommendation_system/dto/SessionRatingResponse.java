package com.example.recommendation_system.dto;

public record SessionRatingResponse(
        String sessionId,
        int savedCount
) {
}
