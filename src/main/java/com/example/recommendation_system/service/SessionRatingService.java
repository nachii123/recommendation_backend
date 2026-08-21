package com.example.recommendation_system.service;

import com.example.recommendation_system.dto.RatedMovieResponse;
import com.example.recommendation_system.dto.SessionRatingRequest;
import com.example.recommendation_system.dto.SessionRatingResponse;

public interface SessionRatingService {
    SessionRatingResponse saveRatings(SessionRatingRequest request);
    RatedMovieResponse getRatedMovies(String sessionId);
}
