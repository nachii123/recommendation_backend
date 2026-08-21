package com.example.recommendation_system.service.impl;

import com.example.recommendation_system.dto.RatedMovieInput;
import com.example.recommendation_system.dto.RecommendationContext;
import com.example.recommendation_system.dto.RecommendationItem;
import com.example.recommendation_system.dto.RecommendationMode;
import com.example.recommendation_system.dto.RecommendationRequest;
import com.example.recommendation_system.entity.Movie;
import com.example.recommendation_system.entity.Rating;
import com.example.recommendation_system.repository.MovieRepository;
import com.example.recommendation_system.repository.RatingRepository;
import com.example.recommendation_system.repository.SessionRatingRepository;
import com.example.recommendation_system.service.RecommendationService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationServiceImpl implements RecommendationService {

    private final MovieRepository movieRepository;
    private final RatingRepository ratingRepository;
    private final SessionRatingRepository sessionRatingRepository;

    public RecommendationServiceImpl(MovieRepository movieRepository, RatingRepository ratingRepository, SessionRatingRepository sessionRatingRepository) {
        this.movieRepository = movieRepository;
        this.ratingRepository = ratingRepository;
        this.sessionRatingRepository = sessionRatingRepository;
    }

    @Override
    public List<RecommendationItem> recommend(RecommendationRequest request) {
        RecommendationContext context = normalize(request);

        // For FILTER_BASED with genres specified, use the targeted DB query path
        // instead of scanning all movies — much faster and correctly enforces genre match
        if (context.mode() == RecommendationMode.FILTER_BASED && !context.selectedGenres().isEmpty()) {
            String keyword = context.keyword() != null && !context.keyword().isBlank()
                    ? context.keyword() : null;

            // If keyword is also set, run keyword search and post-filter by genre
            if (keyword != null) {
                Set<String> normalizedGenres = context.selectedGenres();
                return recommendByKeyword(keyword, context.limit() * 3).stream()
                        .filter(item -> item.genres().stream()
                                .map(g -> g.toLowerCase(Locale.ROOT))
                                .anyMatch(normalizedGenres::contains))
                        .limit(context.limit())
                        .toList();
            }

            // Genre-only filter — use targeted DB query
            return recommendByGenres(
                    context.selectedGenres().stream().toList(),
                    context.minYear(),
                    context.maxYear(),
                    context.limit()
            );
        }

        // RATING_BASED or no genres specified — use the full scoring pipeline
        Map<Long, List<Rating>> ratingsByMovie = ratingRepository.findAll().stream()
                .collect(Collectors.groupingBy(r -> r.getMovie().getId()));

        List<RatedMovieInput> effectiveRatings = resolveRatings(request);
        Set<Long> excludedMovieIds = context.mode() == RecommendationMode.RATING_BASED
                ? extractRatedMovieIds(effectiveRatings)
                : Set.of();

        TasteProfile tasteProfile = context.mode() == RecommendationMode.RATING_BASED
                ? buildTasteProfile(effectiveRatings)
                : TasteProfile.empty();

        return movieRepository.findAll().stream()
                .filter(movie -> !excludedMovieIds.contains(movie.getId()))
                .map(movie -> scoreMovie(movie, ratingsByMovie.getOrDefault(movie.getId(), List.of()), context, tasteProfile))
                .filter(item -> item.score() > Double.NEGATIVE_INFINITY)
                .sorted(Comparator.comparingDouble(RecommendationItem::score).reversed())
                .limit(context.limit())
                .toList();
    }

    @Override
    public List<RecommendationItem> recommendByKeyword(String rawKeyword, int limit) {
        if (rawKeyword == null || rawKeyword.isBlank()) {
            return List.of();
        }
        String keyword = rawKeyword.trim();

        // Build a set of keyword variants to improve matching:
        // "spiderman" → also try "spider-man", "spider man"
        // "lordoftherings" → also try "lord of the rings"
        Set<String> variants = buildKeywordVariants(keyword);

        Set<Long> seenIds = new HashSet<>();
        List<Movie> movies = new java.util.ArrayList<>();
        for (String variant : variants) {
            List<Movie> batch = movieRepository.searchByKeyword(variant, limit * 3);
            for (Movie m : batch) {
                if (seenIds.add(m.getId())) {
                    movies.add(m);
                }
            }
        }

        if (movies.isEmpty()) {
            return List.of();
        }

        List<Long> movieIds = movies.stream().map(Movie::getId).toList();
        Map<Long, Double> avgRatings = ratingRepository.findAverageRatingsByMovieIds(movieIds);
        Map<Long, Long> ratingCounts = ratingRepository.findRatingCountsByMovieIds(movieIds);

        return movies.stream()
                .map(movie -> {
                    double avg = avgRatings.getOrDefault(movie.getId(), 0.0);
                    int count = ratingCounts.getOrDefault(movie.getId(), 0L).intValue();
                    double popularityScore = count == 0 ? 0.0 : Math.min(1.0, Math.log10(count + 1) / 3.0);
                    double qualityScore = avg == 0.0 ? 0.0 : avg / 5.0;
                    double score = round((0.5 * qualityScore) + (0.3 * popularityScore)
                            + (movie.getPopularity() != null ? Math.min(0.2, movie.getPopularity() / 1000.0) : 0.0));
                    return new RecommendationItem(
                            movie.getId(), movie.getTitle(), movie.getTitleClean(), movie.getYear(),
                            movie.getGenreList(), movie.getPosterPath(), movie.getMoviePathUrl(),
                            score, round(avg), count,
                            "Matched your search for \"" + keyword + "\""
                    );
                })
                .sorted(Comparator.comparingDouble(RecommendationItem::score).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Generates keyword search variants to handle common mismatches:
     * - "spiderman"    → "spiderman", "spider-man", "spider man"
     * - "spider-man"   → "spider-man", "spiderman", "spider man"
     * - "lordoftherings" stays as-is (overview search usually picks it up)
     */
    private Set<String> buildKeywordVariants(String keyword) {
        Set<String> variants = new LinkedHashSet<>();
        variants.add(keyword); // always try the original first

        String lower = keyword.toLowerCase(Locale.ROOT);

        // If keyword has no spaces or hyphens (e.g. "spiderman"), try inserting a hyphen
        // at common split points by checking known patterns or simply splitting camelCase runs
        if (!lower.contains("-") && !lower.contains(" ")) {
            // Try inserting a hyphen between letters where a word boundary likely is
            // e.g. "spiderman" → "spider-man", "batman" stays (too short to split usefully)
            String hyphenated = insertHyphen(lower);
            if (hyphenated != null) {
                variants.add(hyphenated);
                variants.add(hyphenated.replace("-", " ")); // "spider man"
            }
        }

        // If keyword has a hyphen (e.g. "spider-man"), also try without
        if (lower.contains("-")) {
            variants.add(lower.replace("-", "")); // "spiderman"
            variants.add(lower.replace("-", " ")); // "spider man"
        }

        // If keyword has spaces (e.g. "spider man"), also try hyphenated and merged
        if (lower.contains(" ")) {
            variants.add(lower.replace(" ", "-")); // "spider-man"
            variants.add(lower.replace(" ", ""));  // "spiderman"
        }

        return variants;
    }

    /**
     * Tries to insert a hyphen at the most likely split point for known compound words.
     * Uses a simple vowel-consonant transition heuristic for short words.
     */
    private String insertHyphen(String word) {
        // Known franchise name mappings
        Map<String, String> knownSplits = Map.of(
                "spiderman", "spider-man",
                "ironman", "iron-man",
                "antman", "ant-man",
                "superman", "super-man",
                "batman", "bat-man",
                "xmen", "x-men"
        );
        return knownSplits.get(word);
    }

    @Override
    public List<RecommendationItem> recommendByGenres(List<String> genres, Integer minYear, Integer maxYear, int limit) {
        if (genres == null || genres.isEmpty()) {
            return List.of();
        }

        // Query per genre and merge — genre_ids is a comma-separated string field
        Set<Long> seenIds = new HashSet<>();
        List<Movie> movies = new java.util.ArrayList<>();
        for (String genre : genres) {
            List<Movie> batch = movieRepository.searchByGenreAndYear(genre, minYear, maxYear, limit * 2);
            for (Movie m : batch) {
                if (seenIds.add(m.getId())) {
                    movies.add(m);
                }
            }
        }
        if (movies.isEmpty()) {
            return List.of();
        }

        List<Long> movieIds = movies.stream().map(Movie::getId).toList();
        Map<Long, Double> avgRatings = ratingRepository.findAverageRatingsByMovieIds(movieIds);
        Map<Long, Long> ratingCounts = ratingRepository.findRatingCountsByMovieIds(movieIds);

        Set<String> normalizedGenres = genres.stream()
                .map(g -> g.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        return movies.stream()
                .map(movie -> {
                    double avg = avgRatings.getOrDefault(movie.getId(), 0.0);
                    int count = ratingCounts.getOrDefault(movie.getId(), 0L).intValue();
                    Set<String> movieGenres = movie.getGenreList().stream()
                            .map(g -> g.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
                    long overlap = movieGenres.stream().filter(normalizedGenres::contains).count();
                    double genreScore = (double) overlap / normalizedGenres.size();
                    double popularityScore = count == 0 ? 0.0 : Math.min(1.0, Math.log10(count + 1) / 3.0);
                    double qualityScore = avg == 0.0 ? 0.0 : avg / 5.0;
                    double score = round((0.45 * genreScore) + (0.30 * qualityScore) + (0.25 * popularityScore));
                    String matchedGenres = movieGenres.stream()
                            .filter(normalizedGenres::contains)
                            .map(g -> g.substring(0, 1).toUpperCase() + g.substring(1))
                            .collect(Collectors.joining(", "));
                    return new RecommendationItem(
                            movie.getId(), movie.getTitle(), movie.getTitleClean(), movie.getYear(),
                            movie.getGenreList(), movie.getPosterPath(), movie.getMoviePathUrl(),
                            score, round(avg), count,
                            "Matched genres: " + matchedGenres
                    );
                })
                .sorted(Comparator.comparingDouble(RecommendationItem::score).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<RecommendationItem> recommendBySessionRatings(String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }

        List<com.example.recommendation_system.entity.SessionRating> sessionRatings =
                sessionRatingRepository.findBySessionId(sessionId.trim());

        if (sessionRatings.isEmpty()) {
            return List.of();
        }

        // Sort by timestamp descending — most recently rated first
        List<com.example.recommendation_system.entity.SessionRating> sorted = sessionRatings.stream()
                .filter(r -> r.getRating() != null)
                .sorted(Comparator.comparingLong(
                        r -> -( r.getTimestamp() != null ? r.getTimestamp() : 0L)))
                .toList();

        Set<Long> ratedMovieIds = sorted.stream()
                .map(r -> r.getMovie().getId())
                .collect(Collectors.toSet());

        // Build a weighted genre profile:
        // Most recent liked movie gets weight 1.0, next gets 0.8, then 0.6, floor at 0.2
        // This makes recommendations reflect what the user liked *recently*, not just overall
        Map<String, Double> genreWeights = new LinkedHashMap<>();
        double weight = 1.0;
        for (com.example.recommendation_system.entity.SessionRating sr : sorted) {
            if (sr.getRating() >= 3) {
                for (String genre : sr.getMovie().getGenreList()) {
                    String g = genre.toLowerCase(Locale.ROOT);
                    // Accumulate weights — a genre appearing in multiple liked movies gets boosted
                    genreWeights.merge(g, weight, Double::sum);
                }
                weight = Math.max(0.2, weight - 0.2);
            }
        }

        if (genreWeights.isEmpty()) {
            // All ratings were low — return popular movies excluding already-rated ones
            return recommendByGenres(List.of(), null, null, limit).stream()
                    .filter(item -> !ratedMovieIds.contains(item.movieId()))
                    .limit(limit)
                    .toList();
        }

        // Take top genres by accumulated weight (most recently + frequently liked)
        List<String> topGenres = genreWeights.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(4)
                .map(Map.Entry::getKey)
                .toList();

        // Fetch candidates from DB by those genres, exclude already-rated movies
        Set<Long> seenIds = new HashSet<>(ratedMovieIds);
        List<Movie> candidates = new java.util.ArrayList<>();
        for (String genre : topGenres) {
            List<Movie> batch = movieRepository.searchByGenreAndYear(genre, null, null, limit * 4);
            for (Movie m : batch) {
                if (seenIds.add(m.getId())) {
                    candidates.add(m);
                }
            }
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> candidateIds = candidates.stream().map(Movie::getId).toList();
        Map<Long, Double> avgRatings = ratingRepository.findAverageRatingsByMovieIds(candidateIds);
        Map<Long, Long> ratingCounts = ratingRepository.findRatingCountsByMovieIds(candidateIds);

        double totalWeight = genreWeights.values().stream().mapToDouble(Double::doubleValue).sum();

        return candidates.stream()
                .map(movie -> {
                    Set<String> movieGenres = movie.getGenreList().stream()
                            .map(g -> g.toLowerCase(Locale.ROOT))
                            .collect(Collectors.toSet());

                    // Taste score: weighted sum of matching genres / total weight
                    double tasteScore = movieGenres.stream()
                            .filter(genreWeights::containsKey)
                            .mapToDouble(g -> genreWeights.getOrDefault(g, 0.0))
                            .sum() / (totalWeight > 0 ? totalWeight : 1.0);

                    double avg = avgRatings.getOrDefault(movie.getId(), 0.0);
                    int count = ratingCounts.getOrDefault(movie.getId(), 0L).intValue();
                    double popularityScore = count == 0 ? 0.0 : Math.min(1.0, Math.log10(count + 1) / 3.0);
                    double qualityScore = avg == 0.0 ? 0.0 : avg / 5.0;

                    // Recency bonus: newer movies get a small boost
                    Integer year = movie.getYear();
                    double recencyScore = year == null ? 0.0 : Math.max(0.0, Math.min(1.0, (year - 1980) / 45.0));

                    double score = round(
                            (0.50 * tasteScore) +
                            (0.25 * qualityScore) +
                            (0.15 * popularityScore) +
                            (0.10 * recencyScore)
                    );

                    String matchedGenres = movieGenres.stream()
                            .filter(genreWeights::containsKey)
                            .map(g -> g.substring(0, 1).toUpperCase() + g.substring(1))
                            .collect(Collectors.joining(", "));

                    return new RecommendationItem(
                            movie.getId(), movie.getTitle(), movie.getTitleClean(), year,
                            movie.getGenreList(), movie.getPosterPath(), movie.getMoviePathUrl(),
                            score, round(avg), count,
                            "Based on your recent ratings — matched: " + matchedGenres
                    );
                })
                .sorted(Comparator.comparingDouble(RecommendationItem::score).reversed())
                .limit(limit)
                .toList();
    }


    private RecommendationContext normalize(RecommendationRequest request) {
        RecommendationMode mode = request.mode() == null ? RecommendationMode.FILTER_BASED : request.mode();
        int limit = request.limit() == null || request.limit() <= 0 ? 10 : Math.min(request.limit(), 50);
        Set<String> selectedGenres = request.genres() == null ? Set.of() : request.genres().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        String keyword = request.keyword() == null ? "" : request.keyword().trim().toLowerCase(Locale.ROOT);
        return new RecommendationContext(mode, selectedGenres, keyword, request.minYear(), request.maxYear(), limit);
    }

    private List<RatedMovieInput> resolveRatings(RecommendationRequest request) {
        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            List<RatedMovieInput> sessionRatings = sessionRatingRepository.findBySessionId(request.sessionId().trim()).stream()
                    .map(rating -> new RatedMovieInput(rating.getMovie().getId(), rating.getRating()))
                    .toList();
            if (!sessionRatings.isEmpty()) {
                return sessionRatings;
            }
        }
        return request.ratedMovies() == null ? List.of() : request.ratedMovies();
    }

    private Set<Long> extractRatedMovieIds(List<RatedMovieInput> ratedMovies) {
        if (ratedMovies == null) {
            return Set.of();
        }
        return ratedMovies.stream()
                .map(RatedMovieInput::movieId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private TasteProfile buildTasteProfile(List<RatedMovieInput> ratedMovies) {
        if (ratedMovies == null || ratedMovies.isEmpty()) {
            return TasteProfile.empty();
        }

        Map<Long, Integer> ratings = new HashMap<>();
        for (RatedMovieInput input : ratedMovies) {
            if (input == null || input.movieId() == null || input.rating() == null) {
                continue;
            }
            ratings.put(input.movieId(), input.rating());
        }

        if (ratings.isEmpty()) {
            return TasteProfile.empty();
        }

        List<Movie> ratedCatalog = movieRepository.findAllById(ratings.keySet()).stream().toList();
        Set<String> likedGenres = new HashSet<>();
        Set<Long> likedMovieIds = new HashSet<>();

        for (Movie movie : ratedCatalog) {
            Integer rating = ratings.get(movie.getId());
            if (rating == null) {
                continue;
            }
            if (rating >= 3) {
                likedMovieIds.add(movie.getId());
                likedGenres.addAll(movie.getGenreList().stream().map(g -> g.toLowerCase(Locale.ROOT)).toList());
            }
        }

        return new TasteProfile(likedGenres, likedMovieIds, Set.of());
    }

    private RecommendationItem scoreMovie(Movie movie, List<Rating> ratings, RecommendationContext context, TasteProfile tasteProfile) {
        Integer year = movie.getYear();
        if (context.minYear() != null && year != null && year < context.minYear()) {
            return unavailable(movie, ratings, "Filtered out by minimum year");
        }
        if (context.maxYear() != null && year != null && year > context.maxYear()) {
            return unavailable(movie, ratings, "Filtered out by maximum year");
        }
        if (context.keyword() != null && !context.keyword().isBlank()
                && !matchesKeyword(movie, context.keyword())) {
            return unavailable(movie, ratings, "Filtered out by keyword");
        }

        List<String> genres = movie.getGenreList();
        Set<String> normalizedGenres = genres.stream().map(g -> g.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        // Hard genre filter: if genres were requested, movie MUST match at least one
        if (!context.selectedGenres().isEmpty()) {
            boolean hasGenreMatch = normalizedGenres.stream().anyMatch(context.selectedGenres()::contains);
            if (!hasGenreMatch) {
                return unavailable(movie, ratings, "Filtered out by genre");
            }
        }

        double avgRating = ratings.stream().mapToInt(Rating::getRating).average().orElse(0.0);
        int ratingCount = ratings.size();
        double popularityScore = ratingCount == 0 ? 0.0 : Math.min(1.0, Math.log10(ratingCount + 1) / 3.0);
        double qualityScore = avgRating == 0.0 ? 0.0 : avgRating / 5.0;
        double recencyScore = year == null ? 0.0 : Math.max(0.0, Math.min(1.0, (year - 1980) / 45.0));

        double score;
        String reason;

        if (context.mode() == RecommendationMode.RATING_BASED && !tasteProfile.isEmpty()) {
            double positiveOverlap = overlapRatio(normalizedGenres, tasteProfile.likedGenres());
            double negativeOverlap = overlapRatio(normalizedGenres, tasteProfile.dislikedGenres());
            double tasteScore = (0.60 * positiveOverlap) - (0.30 * negativeOverlap);
            double noveltyBoost = tasteProfile.likedMovieIds().contains(movie.getId()) ? -1.0 : 0.05;
            score = (0.50 * tasteScore) + (0.20 * qualityScore) + (0.15 * popularityScore) + (0.10 * recencyScore) + noveltyBoost;
            reason = buildTasteReason(genres, avgRating, ratingCount, tasteProfile);
        } else {
            double selectedGenreScore = context.selectedGenres().isEmpty()
                    ? 0.0
                    : overlapRatio(normalizedGenres, context.selectedGenres());
            score = (0.45 * selectedGenreScore) + (0.25 * qualityScore) + (0.20 * popularityScore) + (0.10 * recencyScore);
            reason = buildFilterReason(genres, avgRating, ratingCount, context);
        }

        return new RecommendationItem(
                movie.getId(),
                movie.getTitle(),
                movie.getTitleClean(),
                year,
                genres,
                movie.getPosterPath(),
                movie.getMoviePathUrl(),
                round(score),
                round(avgRating),
                ratingCount,
                reason
        );
    }

    private double overlapRatio(Set<String> movieGenres, Set<String> referenceGenres) {
        if (referenceGenres == null || referenceGenres.isEmpty()) {
            return 0.0;
        }
        long matches = movieGenres.stream().filter(referenceGenres::contains).count();
        return (double) matches / referenceGenres.size();
    }

    private RecommendationItem unavailable(Movie movie, List<Rating> ratings, String reason) {
        return new RecommendationItem(
                movie.getId(),
                movie.getTitle(),
                movie.getTitleClean(),
                movie.getYear(),
                movie.getGenreList(),
                movie.getPosterPath(),
                movie.getMoviePathUrl(),
                Double.NEGATIVE_INFINITY,
                round(ratings.stream().mapToInt(Rating::getRating).average().orElse(0.0)),
                ratings.size(),
                reason
        );
    }

    private String buildFilterReason(List<String> genres, double avgRating, int ratingCount, RecommendationContext context) {
        String genreReason = context.selectedGenres().isEmpty()
                ? "Ranked by overall quality and popularity"
                : "Matched selected genres: " + genres.stream()
                .filter(g -> context.selectedGenres().contains(g.toLowerCase(Locale.ROOT)))
                .collect(Collectors.joining(", "));
        String popularityReason = ratingCount > 0 ? " based on " + ratingCount + " ratings" : " with no historical ratings";
        String keywordReason = context.keyword() == null || context.keyword().isBlank() ? "" : " and keyword filter";
        return genreReason + popularityReason + keywordReason + ", average rating " + round(avgRating);
    }

    private String buildTasteReason(List<String> genres, double avgRating, int ratingCount, TasteProfile tasteProfile) {
        String likedOverlap = genres.stream()
                .filter(g -> tasteProfile.likedGenres().contains(g.toLowerCase(Locale.ROOT)))
                .collect(Collectors.joining(", "));
        StringBuilder reason = new StringBuilder();
        if (!likedOverlap.isBlank()) {
            reason.append("Similar to your liked genres: ").append(likedOverlap);
        } else {
            reason.append("Related to your rated movies");
        }
        reason.append(ratingCount > 0 ? "; based on " + ratingCount + " ratings" : "; with no historical ratings");
        reason.append(", average rating ").append(round(avgRating));
        return reason.toString();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private boolean matchesKeyword(Movie movie, String keyword) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isBlank()) {
            return true;
        }
        return normalize(movie.getTitle()).contains(normalizedKeyword)
                || normalize(movie.getOriginalTitle()).contains(normalizedKeyword)
                || normalize(movie.getContentText()).contains(normalizedKeyword);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private record TasteProfile(
            Set<String> likedGenres,
            Set<Long> likedMovieIds,
            Set<String> dislikedGenres
    ) {
        static TasteProfile empty() {
            return new TasteProfile(Set.of(), Set.of(), Set.of());
        }

        boolean isEmpty() {
            return likedGenres.isEmpty();
        }
    }
}
