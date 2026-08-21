package com.example.recommendation_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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
        name = "ratings",
        indexes = {
                @jakarta.persistence.Index(name = "idx_ratings_movie_id", columnList = "movie_id"),
                @jakarta.persistence.Index(name = "idx_ratings_user_id", columnList = "user_id")
        }
)
public class Rating {

    @EmbeddedId
    private RatingId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @MapsId("movieId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    @Transient
    public Instant getRatedAt() {
        return timestamp == null ? null : Instant.ofEpochSecond(timestamp);
    }
}
