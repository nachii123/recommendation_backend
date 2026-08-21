# Movie Recommendation System

This repository contains the backend for an anonymous movie recommendation system built around the MovieLens 1M dataset.

The intended consumption model is API-first: the UI team integrates the endpoints directly, so the backend does not depend on login or user identity.

## Problem statement

Build a functional recommendation system that can serve ranked movie suggestions, explain why they were selected, and be evaluated as an engineering solution rather than a black-box demo.

## Use case

Movie recommendations for an unauthenticated browsing experience. The evaluator can select genres, optional keyword and year filters, and receive ranked results.

## Recommendation approach

The current recommender is a deterministic hybrid scorer:

- Genre overlap with the request
- Historical quality from average rating
- Popularity from rating volume
- Light recency bonus from release year

This is intentionally transparent and easy to validate in a technical assessment.

## API contract

### `POST /api/recommendations`

Request:

```json
{
  "mode": "FILTER_BASED",
  "genres": ["Action", "Sci-Fi"],
  "keyword": "Matrix",
  "minYear": 1990,
  "maxYear": 2026,
  "limit": 10,
  "ratedMovies": []
}
```

For seeded personalization:

```json
{
  "mode": "RATING_BASED",
  "ratedMovies": [
    { "movieId": 1, "rating": 5 },
    { "movieId": 260, "rating": 4 }
  ],
  "limit": 10
}
```

Response:

```json
{
  "strategy": "Anonymous content-based + popularity hybrid",
  "explanation": "No login is required...",
  "recommendations": [
    {
      "movieId": 2571,
      "title": "Matrix, The (1999)",
      "titleClean": "Matrix, The",
      "year": 1999,
      "genres": ["Action", "Sci-Fi", "Thriller"],
      "score": 0.92,
      "averageRating": 4.67,
      "ratingCount": 3500,
      "reason": "Matched genres..."
    }
  ]
}
```

## System architecture

- Spring Boot REST API
- JPA entities for `users`, `movies`, and `ratings`
- Recommendation service layer with explainable scoring
- CORS enabled for frontend integration

## Dataset

MovieLens 1M CSV imports:

- `users.csv`
- `movies.csv`
- `ratings.csv`

The database is expected to contain those tables before the API is called.

## Technologies used

- Java 17
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Lombok

## Assumptions

- No login is required
- The frontend will call backend APIs directly
- MovieLens data is preloaded in the database
- Recommendation evaluation is based on offline inspection and ranking quality rather than live A/B testing

## Evaluation methodology

Recommended metrics for this system:

- Precision@K
- Recall@K
- NDCG@K
- Coverage
- Diversity
- Latency

For the current anonymous setup, offline evaluation is the practical fit because there is no authenticated user history on the UI.

### Mode behavior

- `FILTER_BASED`: intended for new users, works from filters and catalog signals
- `RATING_BASED`: intended for users who rate a few starter movies, builds a temporary taste profile and excludes already-rated titles

## Test cases

### Successful scenarios

- Action/Sci-Fi requests return movies with matching genres
- Keyword filtering returns titles containing the requested term
- Year bounds constrain the output correctly

### Failure scenarios

- Very narrow filters may return few or no results
- Popularity-biased ranking can surface mainstream titles over niche ones
- The current heuristic does not learn personalized taste from a session

## Limitations

- No live personalization because the UI flow is anonymous
- Ranking is heuristic, not a trained ML model
- The backend expects imported database tables

## Future improvements

- Add session-based anonymous personalization
- Add collaborative filtering from rating neighborhoods
- Add a learned ranking model
- Add CSV ingestion automation and scheduled refresh
- Add formal offline evaluation scripts

## Running locally

1. Configure PostgreSQL in `src/main/resources/application.yaml`
2. Import the MovieLens tables
3. Run:

```bash
./gradlew.bat bootRun
```

## Frontend handoff notes

- The UI team should call `POST /api/recommendations`
- No `userId` is required or expected
- Pass `mode=FILTER_BASED` for new users
- Pass `mode=RATING_BASED` with `ratedMovies` for personalization
- CORS can be configured through `app.cors.allowed-origins`
