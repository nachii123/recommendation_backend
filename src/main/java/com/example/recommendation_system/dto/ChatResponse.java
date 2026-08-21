package com.example.recommendation_system.dto;

import java.util.List;

public record ChatResponse(
        String intent,
        String reply,
        boolean recommendationTriggered,
        RecommendationMode mode,
        RecommendationRequest recommendationRequest,
        List<RecommendationItem> recommendations
) {
}
