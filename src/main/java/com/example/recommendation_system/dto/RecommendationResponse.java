package com.example.recommendation_system.dto;

import java.util.List;

public record RecommendationResponse(
        List<RecommendationItem> recommendations,
        String strategy,
        String explanation,
        RecommendationMode mode
) {
}
