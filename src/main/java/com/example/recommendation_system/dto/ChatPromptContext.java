package com.example.recommendation_system.dto;

import java.util.List;

public record ChatPromptContext(
        String sessionId,
        List<String> likedMovieTitles,
        List<String> likedGenres,
        String message
) {
}
