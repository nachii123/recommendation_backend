package com.example.recommendation_system.dto;

import java.util.List;

public record RatedMovieResponse(
        String sessionId,
        int totalRated,
        List<RatedMovieEntry> ratedMovies
) {
    public record RatedMovieEntry(
            Long movieId,
            String title,
            String titleClean,
            Integer year,
            List<String> genres,
            String posterUrl,
            Integer rating,
            long ratedAt   // epoch seconds
    ) {}
}
