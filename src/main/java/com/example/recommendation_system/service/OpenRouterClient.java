package com.example.recommendation_system.service;

import com.example.recommendation_system.dto.ChatPromptContext;

public interface OpenRouterClient {
    String analyzeMovieIntent(ChatPromptContext context);
}
