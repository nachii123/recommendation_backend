package com.example.recommendation_system.dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;


public record RecommendationItem(
        Long movieId,
        String title,
        String titleClean,
        Integer year,
        List<String> genres,
        String posterPath,
        String moviePathUrl,
        Double score,
        Double averageRating,
        Integer ratingCount,
        String reason
) {

}
