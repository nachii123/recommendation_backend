package com.example.recommendation_system.service.impl;

import com.example.recommendation_system.config.OpenRouterProperties;
import com.example.recommendation_system.dto.ChatPromptContext;
import com.example.recommendation_system.service.OpenRouterClient;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class OpenRouterClientImpl implements OpenRouterClient {

    private final OpenRouterProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public OpenRouterClientImpl(OpenRouterProperties properties) {
        this.properties = properties;
    }

    @Override
//    public String analyzeMovieIntent(ChatPromptContext context) {
//        if (properties.apiKey() == null || properties.apiKey().isBlank()
//                || properties.baseUrl() == null || properties.baseUrl().isBlank()) {
//            return fallbackAnalysis(context);
//        }
//
//        String body = """
//                {
//                  "model": "%s",
//                  "messages": [
//                    {
//                      "role": "system",
//                      "content": "You are a movie-only assistant. Reply only about movie recommendations. If the user asks anything off-topic, respond with intent OFF_TOPIC and reply: Sorry, I can only help with movie recommendations. If the user asks for movie recommendations, identify intent and extract optional genres, year range, keyword, or similarity target. Return strict JSON with keys intent, reply, keyword, minYear, maxYear, genres, similarityTitle."
//                    },
//                    {
//                      "role": "user",
//                      "content": "Session liked movies: %s\\nSession liked genres: %s\\nUser message: %s"
//                    }
//                  ],
//                  "temperature": 0.2
//                }
//                """.formatted(
//                properties.defaultModel(),
//                escapeJson(context.likedMovieTitles().toString()),
//                escapeJson(context.likedGenres().toString()),
//                escapeJson(context.message())
//        );
//
//        try {
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create(properties.baseUrl()))
//                    .timeout(Duration.ofSeconds(30))
//                    .header("Content-Type", "application/json")
//                    .header("Authorization", "Bearer " + properties.apiKey())
//                    .header("HTTP-Referer", "http://localhost")
//                    .header("X-Title", "recommendation-system")
//                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
//                    .build();
//
//            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//            if (response.statusCode() >= 200 && response.statusCode() < 300) {
//                return extractAssistantContent(response.body());
//            }
//        } catch (Exception ignored) {
//            // fallback below
//        }
//
//        return fallbackAnalysis(context);
//    }
    public String analyzeMovieIntent(ChatPromptContext context) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()
                || properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            return fallbackAnalysis(context);
        }

        String systemPrompt = """
            You are a movie assistant. Your ONLY job is to parse the user's message and return a JSON object.
            Task: classify the user's message and extract movie search parameters.

            Allowed "intent" values (choose exactly one):
            - "GREETING": user says hi, hello, hey, thanks, thank you, how are you, bye, good morning, or any casual small talk that is NOT a movie request
            - "RECOMMEND": user wants movie suggestions by title, franchise, keyword, genre, or year
            - "SIMILAR": user wants movies similar to one specific title they name
            - "PERSONAL_RECOMMENDATION": user is asking for recommendations based on their own history/ratings (phrases like "show me my recommendations", "what should I watch", "based on my history", "my taste", "for me")
            - "GENERAL": movie-related question but not a recommendation request (e.g. asking about a director, plot, release date)
            - "OFF_TOPIC": message has nothing to do with movies and is not a greeting (e.g. weather, politics, cooking, sports, math)

            Field rules — read carefully:
            - "keyword": extract ONLY the core movie title, franchise name, actor name, or theme word. Strip ALL conversational filler. Examples: "i need spiderman movie" → "Spider-Man". "show me batman films" → "Batman". "find me some action movies" → "" (use genres array instead).
            - "similarityTitle": ONLY set when the user explicitly says "similar to X" or "like X" and names ONE specific movie. Otherwise leave empty.
            - "genres": array of genre names mentioned or clearly implied. Else [].
            - "minYear"/"maxYear": integers if a year or range is mentioned, else null.
            - "reply": a short friendly natural-language reply (1-2 sentences).

            If intent is GREETING, write a warm natural reply and leave keyword/genres/years empty.
            If intent is OFF_TOPIC, reply exactly: "Sorry, I can only help with movie-related questions." and leave all other fields null/empty.
            If intent is PERSONAL_RECOMMENDATION, set keyword and genres to empty — the system will use session history.

            Respond with ONLY a single valid JSON object — no markdown, no code fences, no explanation:
            {"intent": "", "reply": "", "keyword": "", "minYear": null, "maxYear": null, "genres": [], "similarityTitle": ""}

            Examples:
            User: "hi"
            Output: {"intent": "GREETING", "reply": "Hey there! 👋 I'm your movie assistant. Ask me for recommendations, search by genre, or find movies similar to your favourites!", "keyword": "", "minYear": null, "maxYear": null, "genres": [], "similarityTitle": ""}

            User: "hello how are you"
            Output: {"intent": "GREETING", "reply": "I'm doing great, thanks for asking! 🎬 Ready to help you find your next favourite movie. What are you in the mood for?", "keyword": "", "minYear": null, "maxYear": null, "genres": [], "similarityTitle": ""}

            User: "thanks"
            Output: {"intent": "GREETING", "reply": "You're welcome! 😊 Let me know if you want more movie recommendations.", "keyword": "", "minYear": null, "maxYear": null, "genres": [], "similarityTitle": ""}

            User: "i need spiderman movie"
            Output: {"intent": "RECOMMEND", "reply": "Here are some Spider-Man movies for you!", "keyword": "Spider-Man", "minYear": null, "maxYear": null, "genres": [], "similarityTitle": ""}

            User: "show me batman films"
            Output: {"intent": "RECOMMEND", "reply": "Here are some Batman movies!", "keyword": "Batman", "minYear": null, "maxYear": null, "genres": [], "similarityTitle": ""}

            User: "recommend me some action movies from the 90s"
            Output: {"intent": "RECOMMEND", "reply": "Here are some great 90s action movies!", "keyword": "", "minYear": 1990, "maxYear": 1999, "genres": ["Action"], "similarityTitle": ""}

            User: "find movies similar to The Matrix"
            Output: {"intent": "SIMILAR", "reply": "Here are movies similar to The Matrix!", "keyword": "", "minYear": null, "maxYear": null, "genres": [], "similarityTitle": "The Matrix"}

            User: "show me my recommendations"
            Output: {"intent": "PERSONAL_RECOMMENDATION", "reply": "Here are personalized picks based on your taste!", "keyword": "", "minYear": null, "maxYear": null, "genres": [], "similarityTitle": ""}

            User: "what is the capital of France"
            Output: {"intent": "OFF_TOPIC", "reply": "Sorry, I can only help with movie-related questions.", "keyword": "", "minYear": null, "maxYear": null, "genres": [], "similarityTitle": ""}

            User: "how do I cook pasta"
            Output: {"intent": "OFF_TOPIC", "reply": "Sorry, I can only help with movie-related questions.", "keyword": "", "minYear": null, "maxYear": null, "genres": [], "similarityTitle": ""}
            """;

        String body = """
            {
              "model": "%s",
              "temperature": 0.2,
              "max_tokens": 400,
              "response_format": { "type": "json_object" },
              "messages": [
                {
                  "role": "system",
                  "content": "%s"
                },
                {
                  "role": "user",
                  "content": "Session liked movies: %s\\nSession liked genres: %s\\nUser message: %s"
                }
              ]
            }
            """.formatted(
                properties.defaultModel(),
                escapeJson(systemPrompt),
                escapeJson(context.likedMovieTitles().toString()),
                escapeJson(context.likedGenres().toString()),
                escapeJson(context.message())
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.baseUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("HTTP-Referer", "http://localhost")
                    .header("X-Title", "recommendation-system")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String content = extractAssistantContent(response.body());
                if (content != null && !content.isBlank()) {
                    return stripJsonFences(content);
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }

        return fallbackAnalysis(context);
    }

    private String stripJsonFences(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?", "").trim();
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
            }
        }
        return trimmed;
    }

    // -------------------------------------------------------------------------
    // Fallback classifier — used only when the LLM API is unavailable.
    //
    // Philosophy: classify by positive identification (allow-list), not by
    // blocking known bad words. If the message cannot be positively identified
    // as a greeting or a movie-related request, it is OFF_TOPIC.
    //
    // Flow:
    //   1. Strip an optional leading greeting prefix ("hi", "hey", etc.)
    //   2. If nothing remains  → GREETING
    //   3. If body is a personal-history request → PERSONAL_RECOMMENDATION
    //   4. If body is positively identified as movie-related → RECOMMEND / SIMILAR
    //   5. Everything else   → OFF_TOPIC
    // -------------------------------------------------------------------------

    private String fallbackAnalysis(ChatPromptContext context) {
        String raw = context.message() == null ? "" : context.message().trim();

        if (raw.isBlank()) {
            return offTopic("What movie are you looking for? You can ask by title, genre, or year.");
        }

        String lower = raw.toLowerCase(java.util.Locale.ROOT).trim();

        // 1. Strip greeting prefix ("hi", "hello", "hey …")
        String body = stripGreetingPrefix(lower);
        boolean hadGreeting = !body.equals(lower);

        // 2. Pure greeting — nothing left after stripping
        if (hadGreeting && body.isBlank()) {
            return greeting(greetingReply(lower));
        }

        // 3. Personal recommendation request
        if (isPersonalRequest(body)) {
            return jsonResponse("PERSONAL_RECOMMENDATION",
                    "Here are personalized picks based on your taste!",
                    null, null, null, "[]", null);
        }

        // 4. Positively identify movie intent
        MovieClassification mc = classifyMovieIntent(body);
        if (mc != null) {
            return jsonResponse(mc.intent, mc.reply, mc.keyword, mc.minYear, mc.maxYear, mc.genres, mc.similarityTitle);
        }

        // 5. Cannot positively identify as movie-related → OFF_TOPIC
        return offTopic("Sorry, I can only help with movie-related questions.");
    }

    /**
     * Returns a non-null MovieClassification only when the message is
     * POSITIVELY identified as a movie request.  Returns null otherwise.
     */
    private MovieClassification classifyMovieIntent(String msg) {

        // --- Explicit genre mentions ---
        String detectedGenre = detectGenre(msg);
        if (detectedGenre != null) {
            Integer[] years = extractYearRange(msg);
            return new MovieClassification(
                    "RECOMMEND",
                    "Here are some great " + detectedGenre + " movies!",
                    null,
                    years[0], years[1],
                    "[\"" + detectedGenre + "\"]",
                    null
            );
        }

        // --- "similar to X" / "like X" pattern ---
        String similarTarget = extractSimilarityTarget(msg);
        if (similarTarget != null) {
            return new MovieClassification(
                    "SIMILAR",
                    "Here are movies similar to " + similarTarget + "!",
                    null, null, null, "[]",
                    similarTarget
            );
        }

        // --- Explicit movie-trigger words followed by a meaningful term ---
        // e.g. "show me batman", "i want spiderman", "find matrix movie"
        String keyword = extractMovieKeyword(msg);
        if (keyword != null) {
            Integer[] years = extractYearRange(msg);
            return new MovieClassification(
                    "RECOMMEND",
                    "Here are results for \"" + keyword + "\"!",
                    keyword,
                    years[0], years[1],
                    "[]",
                    null
            );
        }

        // --- Bare year-range request: "movies from 1990s", "films from 2000" ---
        Integer[] years = extractYearRange(msg);
        if (years[0] != null && containsMovieTrigger(msg)) {
            return new MovieClassification(
                    "RECOMMEND",
                    "Here are some movies from that era!",
                    null, years[0], years[1], "[]", null
            );
        }

        return null; // cannot positively identify
    }

    // ---- genre allow-list -----------------------------------------------
    private static final java.util.Map<String, String> GENRE_TRIGGERS = new java.util.LinkedHashMap<>();
    static {
        GENRE_TRIGGERS.put("sci-fi", "Sci-Fi");
        GENRE_TRIGGERS.put("scifi", "Sci-Fi");
        GENRE_TRIGGERS.put("science fiction", "Sci-Fi");
        GENRE_TRIGGERS.put("action", "Action");
        GENRE_TRIGGERS.put("comedy", "Comedy");
        GENRE_TRIGGERS.put("drama", "Drama");
        GENRE_TRIGGERS.put("thriller", "Thriller");
        GENRE_TRIGGERS.put("horror", "Horror");
        GENRE_TRIGGERS.put("romance", "Romance");
        GENRE_TRIGGERS.put("romantic", "Romance");
        GENRE_TRIGGERS.put("animation", "Animation");
        GENRE_TRIGGERS.put("animated", "Animation");
        GENRE_TRIGGERS.put("adventure", "Adventure");
        GENRE_TRIGGERS.put("fantasy", "Fantasy");
        GENRE_TRIGGERS.put("mystery", "Mystery");
        GENRE_TRIGGERS.put("documentary", "Documentary");
        GENRE_TRIGGERS.put("musical", "Musical");
        GENRE_TRIGGERS.put("war", "War");
        GENRE_TRIGGERS.put("western", "Western");
        GENRE_TRIGGERS.put("crime", "Crime");
    }

    private String detectGenre(String msg) {
        for (java.util.Map.Entry<String, String> e : GENRE_TRIGGERS.entrySet()) {
            if (msg.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    // ---- movie trigger words (must precede a meaningful term) ---------------
    private static final java.util.regex.Pattern MOVIE_TRIGGER_PATTERN =
            java.util.regex.Pattern.compile(
                    "\\b(movie|movies|film|films|watch|recommend|recommendation|suggest|cinema|" +
                    "show me|find me|give me|i want|i need|looking for|search for|" +
                    "imdb|director|actor|actress|sequel|franchise|series|trilogy|rated)\\b"
            );

    private boolean containsMovieTrigger(String msg) {
        return MOVIE_TRIGGER_PATTERN.matcher(msg).find();
    }

    /**
     * Returns a keyword only when the message contains an explicit movie-trigger
     * word AND a non-trivial remaining token that could be a title/franchise.
     */
    private String extractMovieKeyword(String msg) {
        if (!containsMovieTrigger(msg)) return null;

        String stripped = msg
                .replaceAll(MOVIE_TRIGGER_PATTERN.pattern(), " ")
                // strip other filler
                .replaceAll("\\b(show|give|find|get|i|need|want|looking|for|can|you|could|" +
                        "please|some|me|the|a|an|help|good|best|great|top|latest|new|old|" +
                        "recent|from|in|of|with|about|any|more|please|similar|like)\\b", " ")
                .replaceAll("[^a-z0-9\\s'-]", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        if (stripped.isBlank() || stripped.length() < 3) return null;

        // Strip year tokens — they are handled separately
        stripped = stripped.replaceAll("\\b(19|20)\\d{2}\\b", "").trim();
        if (stripped.isBlank() || stripped.length() < 3) return null;

        return stripped;
    }

    // ---- similarity extraction -------------------------------------------
    private static final java.util.regex.Pattern SIMILAR_PATTERN =
            java.util.regex.Pattern.compile(
                    "(?:similar to|like|movies like|films like)\\s+([a-z0-9][a-z0-9 ':-]{2,})",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );

    private String extractSimilarityTarget(String msg) {
        java.util.regex.Matcher m = SIMILAR_PATTERN.matcher(msg);
        if (m.find()) {
            String target = m.group(1).trim();
            // Exclude cases where the "target" is just a genre word
            if (detectGenre(target) != null) return null;
            if (target.length() < 3) return null;
            return target;
        }
        return null;
    }

    // ---- year extraction ------------------------------------------------
    private Integer[] extractYearRange(String msg) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\b(19\\d{2}|20\\d{2})s?\\b").matcher(msg);
        Integer first = null, last = null;
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (first == null) first = y;
            last = y;
        }
        // "1990s" → 1990–1999
        if (first != null && msg.matches(".*\\b(19|20)\\d{2}s\\b.*")) {
            last = first + 9;
        }
        return new Integer[]{first, last == null ? first : last};
    }

    // ---- greeting helpers -----------------------------------------------
    private String stripGreetingPrefix(String message) {
        return message
                .replaceFirst("^(hi+|hey+|hello+|hiya|howdy|greetings|yo|sup)[,!.?\\s]+", "")
                .trim();
    }

    private boolean isPureGreeting(String message) {
        return java.util.regex.Pattern.compile(
                "^(hi+|hey+|hello+|hiya|howdy|greetings|good\\s*(morning|afternoon|evening|day)|" +
                "thanks|thank\\s*you|ty|thx|cheers|bye|goodbye|see\\s*ya|" +
                "how\\s*are\\s*you|how\\s*r\\s*u|what'?s\\s*up|wassup|sup|yo)[\\.!?\\s]*$"
        ).matcher(message).matches();
    }

    private String greetingReply(String message) {
        if (message.startsWith("thank") || message.equals("ty") || message.equals("thx") || message.equals("cheers")) {
            return "You're welcome! 😊 Let me know if you want more movie recommendations.";
        }
        if (message.startsWith("bye") || message.startsWith("goodbye") || message.startsWith("see ya")) {
            return "Goodbye! 🎬 Hope you enjoy your movie. Come back anytime for more recommendations!";
        }
        if (message.startsWith("how are") || message.startsWith("how r")
                || message.contains("what's up") || message.contains("wassup") || message.equals("sup")) {
            return "Doing great, thanks for asking! 🎬 Ready to help you find your next favourite movie. What are you in the mood for?";
        }
        return "Hey there! 👋 I'm your movie assistant. Ask me for recommendations, search by genre, or find movies similar to your favourites!";
    }

    private boolean isPersonalRequest(String message) {
        return message.contains("my recommendation") || message.contains("my recommendations")
                || message.contains("my picks") || message.contains("based on my")
                || message.contains("my taste") || message.contains("my history")
                || message.contains("what should i watch") || message.contains("for me based");
    }

    // ---- JSON builders --------------------------------------------------
    private String offTopic(String reply) {
        return "{\"intent\":\"OFF_TOPIC\",\"reply\":\"" + escapeJson(reply)
                + "\",\"keyword\":null,\"minYear\":null,\"maxYear\":null,\"genres\":[],\"similarityTitle\":null}";
    }

    private String greeting(String reply) {
        return "{\"intent\":\"GREETING\",\"reply\":\"" + escapeJson(reply)
                + "\",\"keyword\":null,\"minYear\":null,\"maxYear\":null,\"genres\":[],\"similarityTitle\":null}";
    }

    private String jsonResponse(String intent, String reply, String keyword,
                                Integer minYear, Integer maxYear,
                                String genresJson, String similarityTitle) {
        String kw   = keyword == null        ? "null" : "\"" + escapeJson(keyword) + "\"";
        String sim  = similarityTitle == null ? "null" : "\"" + escapeJson(similarityTitle) + "\"";
        String min  = minYear == null        ? "null" : String.valueOf(minYear);
        String max  = maxYear == null        ? "null" : String.valueOf(maxYear);
        String gen  = genresJson == null     ? "[]"   : genresJson;
        return "{\"intent\":\"" + intent + "\",\"reply\":\"" + escapeJson(reply)
                + "\",\"keyword\":" + kw + ",\"minYear\":" + min + ",\"maxYear\":" + max
                + ",\"genres\":" + gen + ",\"similarityTitle\":" + sim + "}";
    }

    // ---- internal data class --------------------------------------------
    private static class MovieClassification {
        final String intent, reply, keyword, genres, similarityTitle;
        final Integer minYear, maxYear;
        MovieClassification(String intent, String reply, String keyword,
                            Integer minYear, Integer maxYear,
                            String genres, String similarityTitle) {
            this.intent = intent; this.reply = reply; this.keyword = keyword;
            this.minYear = minYear; this.maxYear = maxYear;
            this.genres = genres; this.similarityTitle = similarityTitle;
        }
    }

    private String extractAssistantContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isMissingNode() && !content.isNull()) {
                return content.asText();
            }
        } catch (Exception ignored) {
            // fallback to raw body
        }
        return responseBody;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
