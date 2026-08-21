package com.example.recommendation_system.controller;

import com.example.recommendation_system.dto.ApiErrorResponse;
import com.example.recommendation_system.dto.ChatRequest;
import com.example.recommendation_system.dto.ChatResponse;
import com.example.recommendation_system.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * POST /api/chat
     *
     * Accepts a user message and optional sessionId.
     * Routes the message through intent classification (LLM or local fallback)
     * and returns a chat response with optional movie recommendations.
     *
     * Returns 400 if request body is missing or message is blank.
     * Returns 500 if an unexpected error occurs.
     */
    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", "Request body is required", "/api/chat"));
        }
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", "message must not be blank", "/api/chat"));
        }

        try {
            ChatResponse response = chatService.chat(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(ApiErrorResponse.of(400, "Bad Request", ex.getMessage(), "/api/chat"));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(ApiErrorResponse.of(500, "Internal Server Error",
                            "An unexpected error occurred while processing your request", "/api/chat"));
        }
    }
}
