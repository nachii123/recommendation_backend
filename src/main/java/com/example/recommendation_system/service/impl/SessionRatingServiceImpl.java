package com.example.recommendation_system.service.impl;

import com.example.recommendation_system.dto.RatedMovieResponse;
import com.example.recommendation_system.dto.SessionRatingInput;
import com.example.recommendation_system.dto.SessionRatingRequest;
import com.example.recommendation_system.dto.SessionRatingResponse;
import com.example.recommendation_system.entity.Movie;
import com.example.recommendation_system.entity.SessionRating;
import com.example.recommendation_system.repository.MovieRepository;
import com.example.recommendation_system.repository.SessionRatingRepository;
import com.example.recommendation_system.service.SessionRatingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class SessionRatingServiceImpl implements SessionRatingService {

    private final SessionRatingRepository sessionRatingRepository;
    private final MovieRepository movieRepository;

    public SessionRatingServiceImpl(SessionRatingRepository sessionRatingRepository, MovieRepository movieRepository) {
        this.sessionRatingRepository = sessionRatingRepository;
        this.movieRepository = movieRepository;
    }

    @Override
    @Transactional
    public SessionRatingResponse saveRatings(SessionRatingRequest request) {
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }

        List<SessionRatingInput> ratings = request.ratings() == null ? List.of() : request.ratings();
        int savedCount = 0;

        for (SessionRatingInput input : ratings) {
            if (input == null || input.movieId() == null || input.rating() == null) {
                continue;
            }
            Movie movie = movieRepository.findById(input.movieId())
                    .orElseThrow(() -> new IllegalArgumentException("Movie not found: " + input.movieId()));

            sessionRatingRepository.deleteBySessionIdAndMovie_Id(request.sessionId().trim(), input.movieId());

            SessionRating sessionRating = new SessionRating();
            sessionRating.setSessionId(request.sessionId().trim());
            sessionRating.setMovie(movie);
            sessionRating.setRating(input.rating());
            sessionRating.setTimestamp(java.time.Instant.now().getEpochSecond());
            sessionRatingRepository.save(sessionRating);
            savedCount++;
        }

        return new SessionRatingResponse(request.sessionId().trim(), savedCount);
    }

    @Override
    @Transactional(readOnly = true)
    public RatedMovieResponse getRatedMovies(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required");
        }

        List<SessionRating> ratings = sessionRatingRepository.findBySessionId(sessionId.trim());

        // Sort by timestamp descending — most recently rated first
        List<RatedMovieResponse.RatedMovieEntry> entries = ratings.stream()
                .filter(r -> r.getMovie() != null && r.getRating() != null)
                .sorted(Comparator.comparingLong(
                        r -> -(r.getTimestamp() != null ? r.getTimestamp() : 0L)))
                .map(r -> {
                    Movie m = r.getMovie();
                    return new RatedMovieResponse.RatedMovieEntry(
                            m.getId(),
                            m.getTitle(),
                            m.getTitleClean(),
                            m.getYear(),
                            m.getGenreList(),
                            m.getMoviePathUrl(),
                            r.getRating(),
                            r.getTimestamp() != null ? r.getTimestamp() : 0L
                    );
                })
                .toList();

        return new RatedMovieResponse(sessionId.trim(), entries.size(), entries);
    }
}
