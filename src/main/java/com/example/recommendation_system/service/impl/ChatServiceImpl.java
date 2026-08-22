package com.example.recommendation_system.service.impl;

import com.example.recommendation_system.dto.*;
import com.example.recommendation_system.entity.SessionRating;
import com.example.recommendation_system.repository.SessionRatingRepository;
import com.example.recommendation_system.service.ChatService;
import com.example.recommendation_system.service.OpenRouterClient;
import com.example.recommendation_system.service.RecommendationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ChatServiceImpl implements ChatService {

    private final SessionRatingRepository sessionRatingRepository;
    private final OpenRouterClient openRouterClient;
    private final RecommendationService recommendationService;

    public ChatServiceImpl(SessionRatingRepository sessionRatingRepository,
                           OpenRouterClient openRouterClient,
                           RecommendationService recommendationService) {
        this.sessionRatingRepository = sessionRatingRepository;
        this.openRouterClient = openRouterClient;
        this.recommendationService = recommendationService;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId() == null ? "" : request.sessionId().trim();
        String message   = request.message()   == null ? "" : request.message().trim();

        if (message.isBlank()) {
            return clarification("What movie are you looking for? Ask by title, genre, year, or say 'show me my recommendations'.");
        }

        // Build session context sent to the LLM so it can personalise its reply
        List<SessionRating> sessionRatings = sessionRatingRepository.findBySessionId(sessionId);

        List<String> likedTitles = sessionRatings.stream()
                .filter(r -> r.getRating() != null && r.getRating() >= 3)
                .map(r -> r.getMovie().getTitle())
                .distinct()
                .toList();

        List<String> likedGenres = sessionRatings.stream()
                .filter(r -> r.getRating() != null && r.getRating() >= 3)
                .map(r -> r.getMovie().getGenreList())
                .flatMap(List::stream)
                .map(g -> g.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        // --- Step 1: LLM classifies intent and extracts search parameters ---
        String rawAnalysis = openRouterClient.analyzeMovieIntent(
                new ChatPromptContext(sessionId, likedTitles, likedGenres, message));
        ChatAnalysis analysis = parseAnalysis(rawAnalysis);

        // --- Step 2: Route by intent — no keyword cleaning, trust the LLM ---
        return switch (analysis.intent()) {

            case OFF_TOPIC -> new ChatResponse(
                    analysis.intent().name(),
                    "Sorry, I can only help with movie-related questions.",
                    false, null, null, List.of()
            );

            case GREETING -> new ChatResponse(
                    analysis.intent().name(),
                    nonBlankOr(analysis.reply(),
                            "Hey there! 👋 I'm your movie assistant. Ask me for recommendations, search by genre, or find movies similar to your favourites!"),
                    false, null, null, List.of()
            );

            case NEEDS_CLARIFICATION -> clarification(
                    nonBlankOr(analysis.reply(),
                            "Could you be more specific? Try a movie title, genre, or year.")
            );

            case PERSONAL_RECOMMENDATION -> {
                List<RecommendationItem> personal =
                        recommendationService.recommendBySessionRatings(sessionId, 10);
                if (personal.isEmpty()) {
                    yield clarification("I don't have any ratings from you yet. Rate a few movies first, then ask again!");
                }
                yield new ChatResponse(
                        analysis.intent().name(),
                        nonBlankOr(analysis.reply(), "Here are personalized picks based on your taste!"),
                        true, RecommendationMode.RATING_BASED, null, personal
                );
            }

            case SIMILAR, MOVIE_SIMILARITY_REQUEST -> {
                // LLM already extracted the specific title the user named
                String target = firstNonBlank(analysis.similarityTitle(), analysis.keyword());
                List<RecommendationItem> similar = List.of();

                if (target != null) {
                    similar = recommendationService.recommendByKeyword(target.trim(), 10);
                }
                // If keyword search found nothing, fall back to same genres
                if (similar.isEmpty() && hasGenres(analysis)) {
                    similar = recommendationService.recommendByGenres(
                            analysis.genres(), analysis.minYear(), analysis.maxYear(), 10);
                }
                yield new ChatResponse(
                        analysis.intent().name(),
                        nonBlankOr(analysis.reply(), "Here are movies you might enjoy!"),
                        !similar.isEmpty(), RecommendationMode.FILTER_BASED, null, similar
                );
            }

            // RECOMMEND, MOVIE_RECOMMENDATION, MOVIE_FILTER_REQUEST, GENERAL
            default -> {
                // GENERAL with no search params — LLM reply carries the answer (e.g. "who directed Inception?")
                if (analysis.intent() == ChatIntent.GENERAL
                        && !hasKeyword(analysis) && !hasGenres(analysis)) {
                    yield new ChatResponse(
                            analysis.intent().name(),
                            nonBlankOr(analysis.reply(),
                                    "I can help with movie questions! Ask for recommendations, search by title or genre."),
                            false, null, null, List.of()
                    );
                }

                List<RecommendationItem> results = List.of();

                // Priority 1: keyword / title / franchise search — use LLM-extracted value directly
                if (hasKeyword(analysis)) {
                    results = recommendationService.recommendByKeyword(analysis.keyword().trim(), 10);
                }

                // Priority 2: genre + year filter
                if (results.isEmpty() && hasGenres(analysis)) {
                    results = recommendationService.recommendByGenres(
                            analysis.genres(), analysis.minYear(), analysis.maxYear(), 10);
                }

                // Priority 3: personalised fallback from session history
                if (results.isEmpty() && !likedGenres.isEmpty()) {
                    results = recommendationService.recommendBySessionRatings(sessionId, 10);
                }

                yield new ChatResponse(
                        analysis.intent().name(),
                        nonBlankOr(analysis.reply(), "Here are some movies based on your request!"),
                        !results.isEmpty(), RecommendationMode.FILTER_BASED, null, results
                );
            }
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ChatResponse clarification(String message) {
        return new ChatResponse(
                ChatIntent.NEEDS_CLARIFICATION.name(),
                message, false, null, null, List.of()
        );
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    /** Returns {@code value} when non-blank, otherwise {@code fallback}. */
    private String nonBlankOr(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private boolean hasKeyword(ChatAnalysis a) {
        return a.keyword() != null && !a.keyword().isBlank();
    }

    private boolean hasGenres(ChatAnalysis a) {
        return a.genres() != null && !a.genres().isEmpty();
    }

    // -------------------------------------------------------------------------
    // JSON parsing — tolerant hand-rolled parser for the LLM response
    // -------------------------------------------------------------------------

    private ChatAnalysis parseAnalysis(String raw) {
        if (raw == null || raw.isBlank()) {
            return fallbackAnalysis();
        }
        String json = raw.trim();
        try {
            String intentValue    = extractNullableString(json, "intent");
            String reply          = extractNullableString(json, "reply");
            String keyword        = extractNullableString(json, "keyword");
            Integer minYear       = extractInteger(json, "minYear");
            Integer maxYear       = extractInteger(json, "maxYear");
            List<String> genres   = extractArray(json, "genres");
            String similarityTitle = extractNullableString(json, "similarityTitle");

            ChatIntent intent = ChatIntent.fromLlm(intentValue);
            return new ChatAnalysis(intent, reply, keyword, minYear, maxYear, genres, similarityTitle);
        } catch (Exception ex) {
            return fallbackAnalysis();
        }
    }

    private ChatAnalysis fallbackAnalysis() {
        return new ChatAnalysis(
                ChatIntent.NEEDS_CLARIFICATION,
                "Could you be more specific? Try a movie title, genre, or year.",
                null, null, null, List.of(), null
        );
    }

    private String extractNullableString(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private Integer extractInteger(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + key + "\"\\s*:\\s*(null|-?\\d+)")
                .matcher(json);
        if (m.find()) {
            String v = m.group(1);
            return "null".equalsIgnoreCase(v) ? null : Integer.valueOf(v);
        }
        return null;
    }

    private List<String> extractArray(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + key + "\"\\s*:\\s*\\[(.*?)]")
                .matcher(json);
        if (m.find()) {
            String body = m.group(1).trim();
            if (body.isBlank()) return List.of();
            return java.util.Arrays.stream(body.split(","))
                    .map(s -> s.replaceAll("^\\s*\"|\"\\s*$", "").trim())
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return List.of();
    }
}
