package com.example.recommendation_system.dto;

import java.time.LocalDate;
import java.util.List;

public record MovieListItem(
        Long id,
        String title,
        String titleClean,
        Integer year,
        String originalLanguage,
        String originalTitle,
        String overview,
        Double popularity,
        String posterPath,
        String moviePathUrl,
        LocalDate releaseDate,
        Boolean video,
        Double voteAverage,
        Integer voteCount,
        List<String> genres
) {
}
