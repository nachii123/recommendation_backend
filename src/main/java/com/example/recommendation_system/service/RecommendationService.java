package com.example.recommendation_system.service;

import com.example.recommendation_system.dto.RecommendationItem;
import com.example.recommendation_system.dto.RecommendationRequest;

import java.util.List;

public interface RecommendationService {

    /** Full scoring pipeline — used by /api/recommendations endpoint */
    List<RecommendationItem> recommend(RecommendationRequest request);

    /** Targeted keyword search — hits DB directly, no full scan */
    List<RecommendationItem> recommendByKeyword(String keyword, int limit);

    /**
     * Genre + optional year filter — hits DB directly with a targeted query.
     * Pass multiple genres; results are ranked by the scoring pipeline.
     */
    List<RecommendationItem> recommendByGenres(List<String> genres, Integer minYear, Integer maxYear, int limit);

    /**
     * Personal recommendations based on session ratings.
     * Loads the user's rated movies, builds a taste profile, scores unseen movies.
     */
    List<RecommendationItem> recommendBySessionRatings(String sessionId, int limit);
}

