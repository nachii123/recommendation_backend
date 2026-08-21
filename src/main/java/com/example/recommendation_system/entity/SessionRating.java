package com.example.recommendation_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "session_ratings",
        schema = "recommendation",
        indexes = {
                @Index(name = "idx_session_ratings_session_id", columnList = "session_id"),
                @Index(name = "idx_session_ratings_movie_id", columnList = "movie_id"),
                @Index(name = "idx_session_ratings_session_movie", columnList = "session_id,movie_id")
        }
)
public class SessionRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    public Instant getRatedAt() {
        return timestamp == null ? null : Instant.ofEpochSecond(timestamp);
    }
}
