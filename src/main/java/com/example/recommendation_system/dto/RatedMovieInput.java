package com.example.recommendation_system.dto;

public record RatedMovieInput(
        Long movieId,
        Integer rating
) {
}
