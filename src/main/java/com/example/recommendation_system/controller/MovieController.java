package com.example.recommendation_system.controller;

import com.example.recommendation_system.dto.ApiErrorResponse;
import com.example.recommendation_system.dto.MovieListItem;
import com.example.recommendation_system.entity.Movie;
import com.example.recommendation_system.repository.MovieRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/movies")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class MovieController {

    private final MovieRepository movieRepository;

    public MovieController(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    /**
     * GET /api/movies?search=matrix&limit=20
     *
     * Lists movies with optional search across title, original title, and overview.
     * Returns 404 if no movies match the search term.
     * Returns 400 if limit is invalid.
     */
    @GetMapping
    public ResponseEntity<?> listMovies(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer limit
    ) {
        if (limit != null && limit < 0) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", "limit must be a positive integer", "/api/movies"));
        }

        int resolvedLimit = limit == null || limit == 0 ? 50 : Math.min(limit, 200);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        List<MovieListItem> movies = movieRepository.findAll().stream()
                .filter(movie -> normalizedSearch.isBlank()
                        || matches(movie.getTitle(), normalizedSearch)
                        || matches(movie.getOriginalTitle(), normalizedSearch)
                        || matches(movie.getOverview(), normalizedSearch))
                .limit(resolvedLimit)
                .map(this::toListItem)
                .toList();

        if (movies.isEmpty()) {
            String message = normalizedSearch.isBlank()
                    ? "No movies found in the catalog."
                    : "No movies found matching \"" + search + "\".";
            return ResponseEntity.status(404)
                    .body(ApiErrorResponse.of(404, "Not Found", message, "/api/movies"));
        }

        return ResponseEntity.ok(movies);
    }

    /**
     * GET /api/movies/by-genre?genre=Action&limit=20
     *
     * Returns movies filtered by a single genre, ordered by release date descending.
     * Returns 400 if genre is blank.
     * Returns 404 if no movies found for the given genre.
     */
    @GetMapping("/by-genre")
    public ResponseEntity<?> listMoviesByGenre(
            @RequestParam String genre,
            @RequestParam(defaultValue = "20") int limit
    ) {
        if (genre == null || genre.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", "genre parameter must not be blank", "/api/movies/by-genre"));
        }

        if (limit < 0) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", "limit must be a positive integer", "/api/movies/by-genre"));
        }

        int resolvedLimit = Math.min(limit == 0 ? 20 : limit, 200);

        List<MovieListItem> movies = movieRepository
                .findByGenreOrderByReleaseDateDesc(genre.trim(), resolvedLimit)
                .stream()
                .map(this::toListItem)
                .toList();

        if (movies.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiErrorResponse.of(404, "Not Found",
                            "No movies found for genre \"" + genre + "\".",
                            "/api/movies/by-genre"));
        }

        return ResponseEntity.ok(movies);
    }

    private MovieListItem toListItem(Movie movie) {
        return new MovieListItem(
                movie.getId(),
                movie.getTitle(),
                movie.getTitleClean(),
                movie.getYear(),
                movie.getOriginalLanguage(),
                movie.getOriginalTitle(),
                movie.getOverview(),
                movie.getPopularity(),
                movie.getPosterPath(),
                movie.getMoviePathUrl(),
                movie.getReleaseDate(),
                movie.getVideo(),
                movie.getVoteAverage(),
                movie.getVoteCount() == null ? null : movie.getVoteCount().intValue(),
                movie.getGenreList()
        );
    }

    private boolean matches(String value, String search) {
        if (value == null || search == null || search.isBlank()) {
            return false;
        }
        return normalize(value).contains(normalize(search));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
