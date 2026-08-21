package com.example.recommendation_system.controller;

import com.example.recommendation_system.dto.ApiErrorResponse;
import com.example.recommendation_system.dto.RecommendationItem;
import com.example.recommendation_system.dto.RecommendationMode;
import com.example.recommendation_system.dto.RecommendationRequest;
import com.example.recommendation_system.dto.RecommendationResponse;
import com.example.recommendation_system.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * POST /api/recommendations
     *
     * Returns ranked movie recommendations based on mode:
     * - FILTER_BASED: uses genres, keyword, year filters
     * - RATING_BASED: uses ratedMovies list to build a taste profile
     *
     * Returns 400 if request body is missing or mode is invalid.
     * Returns 404 if no recommendations found for the given filters.
     */
    @PostMapping
    public ResponseEntity<?> recommend(@RequestBody RecommendationRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", "Request body is required", "/api/recommendations"));
        }

        String strategy = request.mode() == null || request.mode() == RecommendationMode.FILTER_BASED
                ? "Anonymous filter-based hybrid"
                : "Anonymous rating-based hybrid";

        String explanation = request.mode() == null || request.mode() == RecommendationMode.FILTER_BASED
                ? "No login is required. The system ranks movies using selected genres, optional keyword/year filters, average rating, rating volume, and a small recency bonus."
                : "No login is required. The system builds a temporary taste profile from the movies the user rated and recommends related unseen titles.";

        List<RecommendationItem> items = recommendationService.recommend(request);

        if (items.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiErrorResponse.of(404, "Not Found",
                            "No recommendations found for the given filters. Try broadening your genres, keyword, or year range.",
                            "/api/recommendations"));
        }

        return ResponseEntity.ok(new RecommendationResponse(items, strategy, explanation, request.mode()));
    }

    /**
     * GET /api/recommendations/{sessionId}
     *
     * Returns personalized recommendations based on the session's rated movies.
     * Returns 400 if sessionId is blank.
     * Returns 404 if no ratings found for the session.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<?> recommendBySession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "10") int limit) {

        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", "sessionId must not be blank", "/api/recommendations/" + sessionId));
        }

        List<RecommendationItem> recommendations =
                recommendationService.recommendBySessionRatings(sessionId.trim(), Math.min(limit, 50));

        if (recommendations.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiErrorResponse.of(404, "Not Found",
                            "No rated movies found for session \"" + sessionId + "\". Rate some movies first, then call this endpoint again.",
                            "/api/recommendations/" + sessionId));
        }

        return ResponseEntity.ok(new RecommendationResponse(
                recommendations,
                "Session-based personalization",
                "Recommendations built from your session ratings. Movies you already rated are excluded. Results are ranked by taste profile match, average rating, and popularity.",
                RecommendationMode.RATING_BASED
        ));
    }
}
