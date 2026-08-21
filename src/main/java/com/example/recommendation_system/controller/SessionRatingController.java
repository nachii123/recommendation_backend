package com.example.recommendation_system.controller;

import com.example.recommendation_system.dto.ApiErrorResponse;
import com.example.recommendation_system.dto.RatedMovieResponse;
import com.example.recommendation_system.dto.SessionRatingRequest;
import com.example.recommendation_system.dto.SessionRatingResponse;
import com.example.recommendation_system.service.SessionRatingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ratings")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class SessionRatingController {

    private final SessionRatingService sessionRatingService;

    public SessionRatingController(SessionRatingService sessionRatingService) {
        this.sessionRatingService = sessionRatingService;
    }

    /**
     * POST /api/ratings
     *
     * Saves session movie ratings. Replaces any existing rating for the same session + movie.
     * Returns 400 if sessionId is missing or any movieId/rating is invalid.
     * Returns 404 if a movieId does not exist in the catalog.
     */
    @PostMapping
    public ResponseEntity<?> saveRatings(@RequestBody SessionRatingRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", "Request body is required", "/api/ratings"));
        }
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", "sessionId is required", "/api/ratings"));
        }
        if (request.ratings() == null || request.ratings().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", "ratings list must not be empty", "/api/ratings"));
        }

        try {
            SessionRatingResponse response = sessionRatingService.saveRatings(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            // thrown when movieId not found
            return ResponseEntity.status(404)
                    .body(ApiErrorResponse.of(404, "Not Found", ex.getMessage(), "/api/ratings"));
        }
    }

    /**
     * GET /api/ratings/{sessionId}
     *
     * Returns all movies rated by this session, sorted by most recently rated first.
     * Returns 400 if sessionId is blank.
     * Returns 404 if no ratings found for the session.
     *
     * Example: GET /api/ratings/67261e61-8146-4eee-b134-b0b44a8089dd
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<?> getRatedMovies(@PathVariable String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", "sessionId must not be blank", "/api/ratings/" + sessionId));
        }

        RatedMovieResponse response = sessionRatingService.getRatedMovies(sessionId);

        if (response.totalRated() == 0) {
            return ResponseEntity.status(404)
                    .body(ApiErrorResponse.of(404, "Not Found",
                            "No ratings found for session \"" + sessionId + "\".",
                            "/api/ratings/" + sessionId));
        }

        return ResponseEntity.ok(response);
    }
}
