package com.example.recommendation_system.dto;

public record SessionRatingInput(
        Long movieId,
        Integer rating
) {
}
