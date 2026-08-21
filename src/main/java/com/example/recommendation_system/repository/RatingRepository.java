package com.example.recommendation_system.repository;

import com.example.recommendation_system.entity.Rating;
import com.example.recommendation_system.entity.RatingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface RatingRepository extends JpaRepository<Rating, RatingId> {

    @Query("SELECT r FROM Rating r JOIN FETCH r.movie JOIN FETCH r.user WHERE r.user.userId = :userId")
    List<Rating> findByUserId(@Param("userId") Integer userId);

    /**
     * Returns average rating per movie for a given list of movie IDs.
     * Result is a list of [movieId, avgRating] pairs.
     */
    @Query("SELECT r.movie.id, AVG(r.rating) FROM Rating r WHERE r.movie.id IN :movieIds GROUP BY r.movie.id")
    List<Object[]> findAvgRatingsByMovieIdsRaw(@Param("movieIds") List<Long> movieIds);

    /**
     * Returns rating count per movie for a given list of movie IDs.
     * Result is a list of [movieId, count] pairs.
     */
    @Query("SELECT r.movie.id, COUNT(r) FROM Rating r WHERE r.movie.id IN :movieIds GROUP BY r.movie.id")
    List<Object[]> findRatingCountsByMovieIdsRaw(@Param("movieIds") List<Long> movieIds);

    default Map<Long, Double> findAverageRatingsByMovieIds(List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) return Map.of();
        return findAvgRatingsByMovieIdsRaw(movieIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).doubleValue()
                ));
    }

    default Map<Long, Long> findRatingCountsByMovieIds(List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) return Map.of();
        return findRatingCountsByMovieIdsRaw(movieIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()
                ));
    }
}

