package com.example.recommendation_system.controller;

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

    @PostMapping
    public SessionRatingResponse saveRatings(@RequestBody SessionRatingRequest request) {
        return sessionRatingService.saveRatings(request);
    }

    /**
     * GET /api/ratings/{sessionId}
     *
     * Returns all movies rated by this session, sorted by most recently rated first.
     *
     * Example: GET /api/ratings/67261e61-8146-4eee-b134-b0b44a8089dd
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<RatedMovieResponse> getRatedMovies(@PathVariable String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(sessionRatingService.getRatedMovies(sessionId));
    }
}
