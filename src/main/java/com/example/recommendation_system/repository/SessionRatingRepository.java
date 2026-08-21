package com.example.recommendation_system.repository;

import com.example.recommendation_system.entity.SessionRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SessionRatingRepository extends JpaRepository<SessionRating, Long> {

    @Query("SELECT sr FROM SessionRating sr JOIN FETCH sr.movie WHERE sr.sessionId = :sessionId")
    List<SessionRating> findBySessionId(@Param("sessionId") String sessionId);

    void deleteBySessionIdAndMovie_Id(String sessionId, Long movieId);
}
