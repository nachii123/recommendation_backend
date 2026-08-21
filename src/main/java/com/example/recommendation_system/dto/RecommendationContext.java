package com.example.recommendation_system.dto;

import java.util.Set;

public record RecommendationContext(
        RecommendationMode mode,
        Set<String> selectedGenres,
        String keyword,
        Integer minYear,
        Integer maxYear,
        int limit
) {
}
