package com.example.recommendation_system.dto;

public enum ChatIntent {
    // Legacy values (kept for backward compatibility)
    MOVIE_RECOMMENDATION,
    MOVIE_FILTER_REQUEST,
    MOVIE_SIMILARITY_REQUEST,
    // Values the LLM actually returns (see system prompt)
    RECOMMEND,
    SIMILAR,
    GENERAL,
    PERSONAL_RECOMMENDATION,   // "show me my recommendations" / "what should I watch based on my history"
    GREETING,                  // "hi", "hello", "thanks", "how are you", casual small talk
    OFF_TOPIC,
    NEEDS_CLARIFICATION;

    /**
     * Parse intent string from LLM output tolerantly.
     * Handles both legacy enum names and the values defined in the system prompt.
     */
    public static ChatIntent fromLlm(String value) {
        if (value == null || value.isBlank()) return NEEDS_CLARIFICATION;
        String v = value.trim().toUpperCase();
        try {
            return ChatIntent.valueOf(v);
        } catch (IllegalArgumentException ignored) {
            // fuzzy fallback
            if (v.contains("GREETING") || v.contains("GREET") || v.contains("HELLO")) return GREETING;
            if (v.contains("PERSONAL") || v.contains("MY_REC") || v.contains("MY REC")) return PERSONAL_RECOMMENDATION;
            if (v.contains("SIMILAR")) return SIMILAR;
            if (v.contains("RECOMMEND") || v.contains("FILTER") || v.contains("GENERAL")) return RECOMMEND;
            if (v.contains("OFF") || v.contains("TOPIC")) return OFF_TOPIC;
            return NEEDS_CLARIFICATION;
        }
    }

    public boolean isOffTopic() {
        return this == OFF_TOPIC;
    }

    public boolean isPersonal() {
        return this == PERSONAL_RECOMMENDATION;
    }

    public boolean isRecommendation() {
        return this == RECOMMEND || this == MOVIE_RECOMMENDATION
                || this == MOVIE_FILTER_REQUEST || this == SIMILAR
                || this == MOVIE_SIMILARITY_REQUEST || this == GENERAL
                || this == PERSONAL_RECOMMENDATION;
    }
}
