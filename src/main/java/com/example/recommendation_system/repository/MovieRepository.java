package com.example.recommendation_system.repository;

import com.example.recommendation_system.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    Optional<Movie> findFirstByTitleContainingIgnoreCaseOrOriginalTitleContainingIgnoreCase(
            String title, String originalTitle);

    /**
     * Keyword search: matches title, original_title, or overview.
     */
    @Query("""
            SELECT m FROM Movie m
            WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(m.originalTitle) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(m.overview) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY m.popularity DESC, m.voteAverage DESC
            LIMIT :limit
            """)
    List<Movie> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    // --- Genre + year queries split by null combinations to avoid JDBC null-type binding errors ---

    @Query("""
            SELECT m FROM Movie m
            WHERE LOWER(m.genreIds) LIKE LOWER(CONCAT('%', :genre, '%'))
            ORDER BY m.popularity DESC, m.voteAverage DESC
            LIMIT :limit
            """)
    List<Movie> searchByGenre(@Param("genre") String genre, @Param("limit") int limit);

    @Query("""
            SELECT m FROM Movie m
            WHERE LOWER(m.genreIds) LIKE LOWER(CONCAT('%', :genre, '%'))
              AND FUNCTION('YEAR', m.releaseDate) >= :minYear
            ORDER BY m.popularity DESC, m.voteAverage DESC
            LIMIT :limit
            """)
    List<Movie> searchByGenreAndMinYear(@Param("genre") String genre, @Param("minYear") int minYear, @Param("limit") int limit);

    @Query("""
            SELECT m FROM Movie m
            WHERE LOWER(m.genreIds) LIKE LOWER(CONCAT('%', :genre, '%'))
              AND FUNCTION('YEAR', m.releaseDate) <= :maxYear
            ORDER BY m.popularity DESC, m.voteAverage DESC
            LIMIT :limit
            """)
    List<Movie> searchByGenreAndMaxYear(@Param("genre") String genre, @Param("maxYear") int maxYear, @Param("limit") int limit);

    @Query("""
            SELECT m FROM Movie m
            WHERE LOWER(m.genreIds) LIKE LOWER(CONCAT('%', :genre, '%'))
              AND FUNCTION('YEAR', m.releaseDate) >= :minYear
              AND FUNCTION('YEAR', m.releaseDate) <= :maxYear
            ORDER BY m.popularity DESC, m.voteAverage DESC
            LIMIT :limit
            """)
    List<Movie> searchByGenreAndYearRange(@Param("genre") String genre, @Param("minYear") int minYear, @Param("maxYear") int maxYear, @Param("limit") int limit);

    /**
     * Convenience dispatcher — picks the right query variant based on which year bounds are set.
     * Avoids passing null to JDBC parameters (PSQLException: Unknown Types value).
     */
    default List<Movie> searchByGenreAndYear(String genre, Integer minYear, Integer maxYear, int limit) {
        if (minYear != null && maxYear != null) {
            return searchByGenreAndYearRange(genre, minYear, maxYear, limit);
        } else if (minYear != null) {
            return searchByGenreAndMinYear(genre, minYear, limit);
        } else if (maxYear != null) {
            return searchByGenreAndMaxYear(genre, maxYear, limit);
        } else {
            return searchByGenre(genre, limit);
        }
    }

    /**
     * Genre filter ordered by release_date DESC (most recent first).
     * Used by GET /api/movies/by-genre
     */
    @Query("""
            SELECT m FROM Movie m
            WHERE LOWER(m.genreIds) LIKE LOWER(CONCAT('%', :genre, '%'))
            ORDER BY m.releaseDate DESC NULLS LAST
            LIMIT :limit
            """)
    List<Movie> findByGenreOrderByReleaseDateDesc(@Param("genre") String genre, @Param("limit") int limit);
    @Query("""
            SELECT m FROM Movie m
            WHERE m.id IN :ids
            ORDER BY m.voteAverage DESC
            """)
    List<Movie> findByIdIn(@Param("ids") List<Long> ids);
}

