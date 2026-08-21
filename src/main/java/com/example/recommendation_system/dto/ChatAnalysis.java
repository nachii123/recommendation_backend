package com.example.recommendation_system.dto;

import java.util.List;

public record ChatAnalysis(
        ChatIntent intent,
        String reply,
        String keyword,
        Integer minYear,
        Integer maxYear,
        List<String> genres,
        String similarityTitle
) {
}
