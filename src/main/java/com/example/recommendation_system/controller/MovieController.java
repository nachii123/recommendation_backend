package com.example.recommendation_system.controller;

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

    @GetMapping
    public List<MovieListItem> listMovies(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer limit
    ) {
        int resolvedLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        return movieRepository.findAll().stream()
                .filter(movie -> normalizedSearch.isBlank()
                        || matches(movie.getTitle(), normalizedSearch)
                        || matches(movie.getOriginalTitle(), normalizedSearch)
                        || matches(movie.getOverview(), normalizedSearch))
                .limit(resolvedLimit)
                .map(this::toListItem)
                .toList();
    }

    /**
     * GET /api/movies/by-genre?genre=Action&limit=20
     *
     * Returns movies filtered by a single genre string,
     * ordered by release_date DESC (most recently released first).
     *
     * Examples:
     *   GET /api/movies/by-genre?genre=Action
     *   GET /api/movies/by-genre?genre=Romance&limit=10
     *   GET /api/movies/by-genre?genre=Sci-Fi&limit=50
     */
    @GetMapping("/by-genre")
    public ResponseEntity<List<MovieListItem>> listMoviesByGenre(
            @RequestParam String genre,
            @RequestParam(defaultValue = "20") int limit
    ) {
        if (genre == null || genre.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        int resolvedLimit = Math.min(limit <= 0 ? 20 : limit, 200);

        List<MovieListItem> movies = movieRepository
                .findByGenreOrderByReleaseDateDesc(genre.trim(), resolvedLimit)
                .stream()
                .map(this::toListItem)
                .toList();

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
