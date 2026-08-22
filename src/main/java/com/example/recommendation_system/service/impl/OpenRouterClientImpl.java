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

    private static final String SYSTEM_PROMPT = """
            You are a movie assistant. Your only job is to read the user's message and return a single JSON object.

            Classify the intent into exactly one of these values:
            - "GREETING"              — hi, hello, hey, thanks, how are you, bye, good morning, or any casual small talk that is NOT a movie request
            - "RECOMMEND"             — user wants movie suggestions by title, franchise name, keyword, genre, or year
            - "SIMILAR"               — user wants movies similar to a specific title they name
            - "PERSONAL_RECOMMENDATION" — user asks for picks based on their own history/taste ("what should I watch", "based on my history", "my recommendations")
            - "GENERAL"               — a movie-related question that is not a recommendation request (e.g. about a director, plot, cast, release date)
            - "OFF_TOPIC"             — nothing to do with movies and not a greeting

            Field rules:
            - "keyword": the core title, franchise, or theme. Extract ONLY the meaningful content words — no filler.
              Examples: "I want to watch Spider-Man" → "Spider-Man"
                        "show me batman movies"      → "Batman"
                        "find something like Inception" → "" (use similarityTitle instead)
                        "action movies from the 90s" → "" (use genres + minYear/maxYear instead)
            - "similarityTitle": set ONLY when the user says "similar to X" or "like X" for a specific title. Otherwise "".
            - "genres": array of genre names clearly stated or strongly implied. Use standard names: Action, Comedy, Drama, Thriller, Horror, Romance, Sci-Fi, Animation, Adventure, Fantasy, Mystery, Documentary, Musical, War, Western, Crime. Empty array if none.
            - "minYear" / "maxYear": integer year if mentioned, null otherwise. "90s" → minYear 1990, maxYear 1999.
            - "reply": a short, friendly 1-2 sentence natural language reply.

            Special rules:
            - GREETING → warm reply, leave keyword/genres/years empty.
            - OFF_TOPIC → reply exactly "Sorry, I can only help with movie-related questions." Leave all other fields empty/null.
            - PERSONAL_RECOMMENDATION → leave keyword and genres empty; session history is used automatically.
            - GENERAL with a specific title mentioned → put the title in keyword so the user can see related movies.

            Return ONLY valid JSON, no markdown fences, no explanation:
            {"intent":"","reply":"","keyword":"","minYear":null,"maxYear":null,"genres":[],"similarityTitle":""}
            """;

    private final OpenRouterProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public OpenRouterClientImpl(OpenRouterProperties properties) {
        this.properties = properties;
    }

    @Override
    public String analyzeMovieIntent(ChatPromptContext context) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()
                || properties.baseUrl() == null || properties.baseUrl().isBlank()) {
            return simpleFallback(context);
        }

        String userContent = buildUserContent(context);

        String body = """
                {
                  "model": "%s",
                  "temperature": 0.1,
                  "max_tokens": 300,
                  "response_format": { "type": "json_object" },
                  "messages": [
                    { "role": "system", "content": "%s" },
                    { "role": "user",   "content": "%s" }
                  ]
                }
                """.formatted(
                properties.defaultModel(),
                escapeJson(SYSTEM_PROMPT),
                escapeJson(userContent)
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

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String content = extractAssistantContent(response.body());
                if (content != null && !content.isBlank()) {
                    return stripJsonFences(content);
                }
            }
        } catch (Exception ignored) {
            // fall through to simpleFallback
        }

        return simpleFallback(context);
    }

    /**
     * Fallback used when the LLM API is unavailable.
     *
     * Strategy: treat the raw user message as a keyword search.
     * This is simple and honest — no pretend NLU. The user gets
     * results as long as their message contains a recognisable title
     * or genre that the DB can match via LIKE.
     *
     * Only known greetings short-circuit to a GREETING response.
     */
    private String simpleFallback(ChatPromptContext context) {
        String msg = context.message() == null ? "" : context.message().trim();

        if (msg.isBlank()) {
            return clarificationJson("What movie are you looking for? Try a title, genre, or year.");
        }

        String lower = msg.toLowerCase(java.util.Locale.ROOT);

        // Pure greeting — no movie content
        if (isPureGreeting(lower)) {
            return greetingJson(greetingReply(lower));
        }

        // Personal recommendation request
        if (lower.contains("my recommendation") || lower.contains("my recommendations")
                || lower.contains("my picks") || lower.contains("based on my")
                || lower.contains("my taste") || lower.contains("my history")
                || lower.contains("what should i watch")) {
            return jsonOf("PERSONAL_RECOMMENDATION",
                    "Here are personalized picks based on your taste!", "", null, null, "[]", "");
        }

        // Everything else: pass the raw message as keyword and let the DB LIKE search handle it.
        // The recommendation service's buildKeywordVariants will handle hyphenation variants.
        return jsonOf("RECOMMEND",
                "Let me find some movies for you!",
                msg,   // raw message — RecommendationService strips non-essentials via LIKE
                null, null, "[]", "");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildUserContent(ChatPromptContext context) {
        StringBuilder sb = new StringBuilder();
        if (!context.likedMovieTitles().isEmpty()) {
            sb.append("User's liked movies: ").append(context.likedMovieTitles()).append("\n");
        }
        if (!context.likedGenres().isEmpty()) {
            sb.append("User's liked genres: ").append(context.likedGenres()).append("\n");
        }
        sb.append("User message: ").append(context.message());
        return sb.toString();
    }

    private String stripJsonFences(String content) {
        String t = content.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").trim();
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3).trim();
        }
        return t;
    }

    private String extractAssistantContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isMissingNode() && !content.isNull()) {
                return content.asText();
            }
        } catch (Exception ignored) {
        }
        return responseBody;
    }

    private boolean isPureGreeting(String lower) {
        return lower.matches(
                "(hi+|hey+|hello+|hiya|howdy|greetings|good\\s*(morning|afternoon|evening|day)|" +
                "thanks|thank\\s*you|ty|thx|cheers|bye|goodbye|see\\s*ya|" +
                "how\\s*are\\s*you|how\\s*r\\s*u|what'?s\\s*up|wassup|sup|yo)[.!?\\s]*");
    }

    private String greetingReply(String lower) {
        if (lower.startsWith("thank") || lower.equals("ty") || lower.equals("thx") || lower.equals("cheers")) {
            return "You're welcome! 😊 Let me know if you want more movie recommendations.";
        }
        if (lower.startsWith("bye") || lower.startsWith("goodbye") || lower.startsWith("see ya")) {
            return "Goodbye! 🎬 Hope you enjoy your movie. Come back anytime!";
        }
        if (lower.contains("how are") || lower.contains("what's up") || lower.contains("wassup")) {
            return "Doing great, thanks for asking! 🎬 Ready to help you find your next favourite movie.";
        }
        return "Hey there! 👋 I'm your movie assistant. Ask me for recommendations, search by genre, or find movies similar to your favourites!";
    }

    // ---- JSON builders ------------------------------------------------------

    private String greetingJson(String reply) {
        return jsonOf("GREETING", reply, "", null, null, "[]", "");
    }

    private String clarificationJson(String reply) {
        return jsonOf("NEEDS_CLARIFICATION", reply, "", null, null, "[]", "");
    }

    private String jsonOf(String intent, String reply, String keyword,
                          Integer minYear, Integer maxYear,
                          String genresJson, String similarityTitle) {
        String kw  = keyword == null        ? "\"\"" : "\"" + escapeJson(keyword) + "\"";
        String sim = similarityTitle == null ? "\"\"" : "\"" + escapeJson(similarityTitle) + "\"";
        String min = minYear == null         ? "null"  : String.valueOf(minYear);
        String max = maxYear == null         ? "null"  : String.valueOf(maxYear);
        String gen = genresJson == null      ? "[]"    : genresJson;
        return "{\"intent\":\"" + intent + "\","
                + "\"reply\":\"" + escapeJson(reply) + "\","
                + "\"keyword\":" + kw + ","
                + "\"minYear\":" + min + ","
                + "\"maxYear\":" + max + ","
                + "\"genres\":" + gen + ","
                + "\"similarityTitle\":" + sim + "}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
