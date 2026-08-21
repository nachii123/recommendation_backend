package com.example.recommendation_system.dto;

import java.util.List;

public record RecommendationRequest(
        RecommendationMode mode,
        String sessionId,
        List<String> genres,
        String keyword,
        Integer minYear,
        Integer maxYear,
        Integer limit,
        List<RatedMovieInput> ratedMovies
) {
}
