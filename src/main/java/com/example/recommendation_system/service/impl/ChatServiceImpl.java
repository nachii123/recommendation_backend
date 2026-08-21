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

//    @Override
//    public ChatResponse chat(ChatRequest request) {
//        String sessionId = request.sessionId() == null ? "" : request.sessionId().trim();
//        String message = request.message() == null ? "" : request.message().trim();
//
//        List<String> likedMovieTitles = sessionRatingRepository.findBySessionId(sessionId).stream()
//                .filter(r -> r.getRating() != null && r.getRating() >= 3)
//                .map(r -> r.getMovie().getTitle())
//                .distinct()
//                .toList();
//
//        List<String> likedGenres = sessionRatingRepository.findBySessionId(sessionId).stream()
//                .filter(r -> r.getRating() != null && r.getRating() >= 3)
//                .map(r -> r.getMovie().getGenreList())
//                .flatMap(List::stream)
//                .map(g -> g.toLowerCase(Locale.ROOT))
//                .distinct()
//                .toList();
//
//        String rawAnalysis = openRouterClient.analyzeMovieIntent(new ChatPromptContext(sessionId, likedMovieTitles, likedGenres, message));
//        ChatAnalysis analysis = parseAnalysis(rawAnalysis);
//
//        if (analysis.intent() == ChatIntent.OFF_TOPIC) {
//            return new ChatResponse(
//                    analysis.intent().name(),
//                    "Sorry, I can only help with movie recommendations.",
//                    false,
//                    null,
//                    null,
//                    List.of()
//            );
//        }
//
//        RecommendationRequest recommendationRequest = buildRecommendationRequest(sessionId, message, analysis);
//        List<com.example.recommendation_system.dto.RecommendationItem> recommendations = recommendationService.recommend(recommendationRequest);
//
//        return new ChatResponse(
//                analysis.intent().name(),
//                analysis.reply() == null || analysis.reply().isBlank()
//                        ? "I found some movie recommendations based on your request."
//                        : analysis.reply(),
//                true,
//                recommendationRequest.mode(),
//                recommendationRequest,
//                recommendations
//        );
//    }
    @Override
    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId() == null ? "" : request.sessionId().trim();
        String message = request.message() == null ? "" : request.message().trim();

        if (message.isBlank()) {
            return clarification("What movie are you looking for? You can ask by title, genre, year, or just say 'show me my recommendations'.");
        }

        // Load session context for the LLM (liked titles + genres, used to enrich prompt)
        List<SessionRating> sessionRatingsList = sessionRatingRepository.findBySessionId(sessionId);

        List<String> likedMovieTitles = sessionRatingsList.stream()
                .filter(r -> r.getRating() != null && r.getRating() >= 3)
                .map(r -> r.getMovie().getTitle())
                .distinct()
                .toList();

        List<String> likedGenres = sessionRatingsList.stream()
                .filter(r -> r.getRating() != null && r.getRating() >= 3)
                .map(r -> r.getMovie().getGenreList())
                .flatMap(List::stream)
                .map(g -> g.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        // Step 1: Analyze intent via LLM (or local fallback)
        String rawAnalysis = openRouterClient.analyzeMovieIntent(new ChatPromptContext(sessionId, likedMovieTitles, likedGenres, message));
        ChatAnalysis analysis = parseAnalysis(rawAnalysis);

        // Step 2: Route by intent to the right targeted query
        return switch (analysis.intent()) {

            case OFF_TOPIC -> new ChatResponse(
                    analysis.intent().name(),
                    "Sorry, I can only help with movie-related questions.",
                    false, null, null, List.of()
            );

            case GREETING -> new ChatResponse(
                    analysis.intent().name(),
                    analysis.reply() != null && !analysis.reply().isBlank()
                            ? analysis.reply()
                            : "Hey there! 👋 I'm your movie assistant. Ask me for recommendations, search by genre, or find movies similar to your favourites!",
                    false, null, null, List.of()
            );

            case NEEDS_CLARIFICATION -> clarification(
                    analysis.reply() != null && !analysis.reply().isBlank()
                            ? analysis.reply()
                            : "Could you be more specific? Try mentioning a movie title, genre, or year."
            );

            case PERSONAL_RECOMMENDATION -> {
                // Use session ratings to build a personal taste profile
                List<RecommendationItem> personal = recommendationService.recommendBySessionRatings(sessionId, 10);
                if (personal.isEmpty()) {
                    yield clarification("I don't have any ratings from you yet. Rate a few movies first, then ask again!");
                }
                yield new ChatResponse(
                        analysis.intent().name(),
                        analysis.reply() != null && !analysis.reply().isBlank()
                                ? analysis.reply()
                                : "Here are personalized picks based on your taste!",
                        true, RecommendationMode.RATING_BASED, null, personal
                );
            }

            case SIMILAR, MOVIE_SIMILARITY_REQUEST -> {
                // SIMILAR: user named a specific movie — find it and search by keyword/genre
                String titleTarget = firstNonBlank(analysis.similarityTitle(), analysis.keyword());
                String cleanedTitle = titleTarget != null ? cleanKeyword(titleTarget) : null;
                List<RecommendationItem> similar = List.of();

                if (cleanedTitle != null) {
                    similar = recommendationService.recommendByKeyword(cleanedTitle, 10);
                }
                if (similar.isEmpty() && analysis.genres() != null && !analysis.genres().isEmpty()) {
                    similar = recommendationService.recommendByGenres(
                            analysis.genres(), analysis.minYear(), analysis.maxYear(), 10);
                }
                yield new ChatResponse(
                        analysis.intent().name(),
                        analysis.reply() != null && !analysis.reply().isBlank()
                                ? analysis.reply()
                                : "Here are movies you might enjoy!",
                        !similar.isEmpty(), RecommendationMode.FILTER_BASED, null, similar
                );
            }

            // RECOMMEND, MOVIE_RECOMMENDATION, MOVIE_FILTER_REQUEST, GENERAL — all keyword/genre based
            default -> {
                String keyword = cleanKeyword(firstNonBlank(analysis.keyword(), analysis.similarityTitle()));
                List<RecommendationItem> results = List.of();

                // Priority 1: keyword search (franchise, title, actor)
                if (keyword != null && !keyword.isBlank()) {
                    results = recommendationService.recommendByKeyword(keyword, 10);
                }

                // Priority 2: genre + year filter if keyword returned nothing or wasn't set
                if (results.isEmpty() && analysis.genres() != null && !analysis.genres().isEmpty()) {
                    results = recommendationService.recommendByGenres(
                            analysis.genres(), analysis.minYear(), analysis.maxYear(), 10);
                }

                // Priority 3: if session has history and no specific criteria, fall back to personal
                if (results.isEmpty() && !likedGenres.isEmpty()) {
                    results = recommendationService.recommendBySessionRatings(sessionId, 10);
                }

                yield new ChatResponse(
                        analysis.intent().name(),
                        analysis.reply() != null && !analysis.reply().isBlank()
                                ? analysis.reply()
                                : "Here are some movies based on your request!",
                        !results.isEmpty(), RecommendationMode.FILTER_BASED, null, results
                );
            }
        };
    }

    private ChatResponse clarification(String message) {
        return new ChatResponse(
                ChatIntent.NEEDS_CLARIFICATION.name(),
                message,
                false, null, null, List.of()
        );
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    /**
     * Strips conversational filler words so only the core search term remains.
     * e.g. "i need spiderman movie" → "spiderman"
     *      "spider-man related movies" → "spider-man"
     *      "show me batman films" → "batman"
     */
    private String cleanKeyword(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String cleaned = raw.toLowerCase(Locale.ROOT)
                // multi-word phrases first
                .replaceAll("\\bi am looking for\\b", " ")
                .replaceAll("\\bi am searching for\\b", " ")
                .replaceAll("\\blooking for\\b", " ")
                .replaceAll("\\bwant to watch\\b", " ")
                .replaceAll("\\brelated to\\b", " ")
                .replaceAll("\\bsimilar to\\b", " ")
                // single filler words
                .replaceAll("\\b(show|give|find|get|recommend|suggest|need|want|please|some|me|the|a|an|movie|movies|film|films|watch|help|related|based|could|couly|you|with|this|that|those|these|about|can|for|is|are|was|were|have|has|i|my|your)\\b", " ")
                // strip anything that's not alphanumeric, space, hyphen, or apostrophe
                .replaceAll("[^a-z0-9\\s'-]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        if (cleaned.isBlank()) return null;

        // If still multi-word, check if it looks like a compound name (2 short tokens like "spider man")
        // vs a keyword with leftover noise (3+ tokens). For 3+ tokens, keep only the longest.
        String[] tokens = cleaned.split("\\s+");
        if (tokens.length >= 3) {
            // Pick the two longest adjacent tokens — likely the franchise name
            String longest = java.util.Arrays.stream(tokens)
                    .max(java.util.Comparator.comparingInt(String::length))
                    .orElse(cleaned);
            if (longest.length() >= 4) {
                return longest;
            }
        }

        return cleaned;
    }

    private String cleanSimilarityTitle(String raw) {
        return raw
                .replaceAll("(?i)\\b(movie|film|the)\\b", "")
                .replaceAll("[^a-zA-Z0-9 ]", "")
                .trim();
    }
    private ChatAnalysis parseAnalysis(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ChatAnalysis(ChatIntent.NEEDS_CLARIFICATION, "Tell me which movie genre or style you want.", null, null, null, List.of(), null);
        }
        String normalized = raw.trim();
        try {
            String intentValue = extractString(normalized, "intent");
            String reply = extractString(normalized, "reply");
            String keyword = extractNullableString(normalized, "keyword");
            Integer minYear = extractInteger(normalized, "minYear");
            Integer maxYear = extractInteger(normalized, "maxYear");
            List<String> genres = extractArray(normalized, "genres");
            String similarityTitle = extractNullableString(normalized, "similarityTitle");

            // Use tolerant parsing — handles "RECOMMEND", "SIMILAR", "GENERAL" and legacy enum names
            ChatIntent intent = ChatIntent.fromLlm(intentValue);
            return new ChatAnalysis(intent, reply, keyword, minYear, maxYear, genres, similarityTitle);
        } catch (Exception ex) {
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (lower.contains("sorry, i can only help with movie recommendations")) {
                return new ChatAnalysis(ChatIntent.OFF_TOPIC, "Sorry, I can only help with movie recommendations.", null, null, null, List.of(), null);
            }
            return new ChatAnalysis(ChatIntent.NEEDS_CLARIFICATION, "Tell me which movie genre or style you want.", null, null, null, List.of(), null);
        }
    }

    private String extractString(String json, String key) {
        String value = extractNullableString(json, key);
        return value;
    }

    private String extractNullableString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private Integer extractInteger(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(null|-?\\d+)";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            String value = matcher.group(1);
            if ("null".equalsIgnoreCase(value)) {
                return null;
            }
            return Integer.valueOf(value);
        }
        return null;
    }

    private List<String> extractArray(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\\[(.*?)\\]";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            String body = matcher.group(1).trim();
            if (body.isBlank()) {
                return List.of();
            }
            return java.util.Arrays.stream(body.split(","))
                    .map(s -> s.replaceAll("^\\s*\"|\"\\s*$", "").trim())
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return List.of();
    }

}
