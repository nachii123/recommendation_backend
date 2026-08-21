package com.example.recommendation_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

@Entity
@Table(name = "movies" ,schema = "recommendation")
public class Movie {

    private static final String TMDB_IMAGE_BASE_URL =
            "https://image.tmdb.org/t/p/w500";

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "adult")
    private Boolean adult;

    @Column(name = "backdrop_path", length = 500)
    private String backdropPath;

    @Column(name = "genre_ids", columnDefinition = "text")
    private String genreIds;

    @Column(name = "original_language", length = 20)
    private String originalLanguage;

    @Column(name = "original_title", columnDefinition = "text")
    private String originalTitle;

    @Column(name = "overview", columnDefinition = "text")
    private String overview;

    @Column(name = "popularity")
    private Double popularity;

    @Column(name = "poster_path", length = 500)
    private String posterPath;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "video")
    private Boolean video;

    @Column(name = "vote_average")
    private Double voteAverage;

    @Column(name = "vote_count")
    private Long voteCount;

    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY)
    private List<Rating> ratings;

    public String getTitleClean() {
        if (title == null) {
            return null;
        }
        return title.replaceFirst("\\s*\\(\\d{4}\\)$", "");
    }

    public Integer getYear() {
        if (releaseDate != null) {
            return releaseDate.getYear();
        }
        if (title == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\\((\\d{4})\\)$").matcher(title);
        if (matcher.find()) {
            return Integer.valueOf(matcher.group(1));
        }
        return null;
    }

    public String getMoviePathUrl() {
        if (posterPath == null || posterPath.isBlank()) {
            return null;
        }
        if (posterPath.startsWith("http://") || posterPath.startsWith("https://")) {
            return posterPath;
        }
        return TMDB_IMAGE_BASE_URL + posterPath;
    }

    public List<String> getGenreList() {
        if (genreIds == null || genreIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(genreIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public String getContentText() {
        StringBuilder builder = new StringBuilder();
        if (title != null) {
            builder.append(title).append(' ');
        }
        if (originalTitle != null) {
            builder.append(originalTitle).append(' ');
        }
        if (overview != null) {
            builder.append(overview);
        }
        return builder.toString().trim();
    }
}
