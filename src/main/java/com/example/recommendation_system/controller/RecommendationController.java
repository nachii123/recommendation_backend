package com.example.recommendation_system.controller;

import com.example.recommendation_system.dto.RecommendationItem;
import com.example.recommendation_system.dto.RecommendationMode;
import com.example.recommendation_system.dto.RecommendationRequest;
import com.example.recommendation_system.dto.RecommendationResponse;
import com.example.recommendation_system.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public RecommendationResponse recommend(@RequestBody RecommendationRequest request) {
        String strategy = request.mode() == null || request.mode().name().equals("FILTER_BASED")
                ? "Anonymous filter-based hybrid"
                : "Anonymous rating-based hybrid";
        String explanation = request.mode() == null || request.mode().name().equals("FILTER_BASED")
                ? "No login is required. The system ranks movies using selected genres, optional keyword/year filters, average rating, rating volume, and a small recency bonus."
                : "No login is required. The system builds a temporary taste profile from the movies the user rated and recommends related unseen titles.";
        return new RecommendationResponse(
                recommendationService.recommend(request),
                strategy,
                explanation,
                request.mode()
        );
    }

    /**
     * GET /api/recommendations/{sessionId}
     *
     * Returns personalized movie recommendations for the given session.
     * Reads the session's rated movies from session_ratings, builds a taste profile
     * from liked movies (rating >= 3), and returns scored unseen candidates.
     *
     * Example: GET /api/recommendations/session-123
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<RecommendationResponse> recommendBySession(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "10") int limit) {

        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        List<RecommendationItem> recommendations =
                recommendationService.recommendBySessionRatings(sessionId.trim(), Math.min(limit, 50));

        if (recommendations.isEmpty()) {
            return ResponseEntity.ok(new RecommendationResponse(
                    List.of(),
                    "Session-based personalization",
                    "No rated movies found for session \"" + sessionId + "\". Rate some movies first, then call this endpoint again.",
                    RecommendationMode.RATING_BASED
            ));
        }

        return ResponseEntity.ok(new RecommendationResponse(
                recommendations,
                "Session-based personalization",
                "Recommendations built from your session ratings. Movies you already rated are excluded. Results are ranked by taste profile match, average rating, and popularity.",
                RecommendationMode.RATING_BASED
        ));
    }
}

