package com.example.recommendation_system.dto;

import java.util.List;

public record SessionRatingRequest(
        String sessionId,
        List<SessionRatingInput> ratings
) {
}
