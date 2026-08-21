package com.example.recommendation_system.service;

import com.example.recommendation_system.dto.ChatRequest;
import com.example.recommendation_system.dto.ChatResponse;

public interface ChatService {
    ChatResponse chat(ChatRequest request);
}
