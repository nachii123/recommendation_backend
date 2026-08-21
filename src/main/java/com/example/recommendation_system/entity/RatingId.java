package com.example.recommendation_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class RatingId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "movie_id", nullable = false)
    private Integer movieId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RatingId ratingId = (RatingId) o;
        return Objects.equals(userId, ratingId.userId) && Objects.equals(movieId, ratingId.movieId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, movieId);
    }
}
