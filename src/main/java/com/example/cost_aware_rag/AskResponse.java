package com.example.cost_aware_rag;

public record AskResponse(
        String answer,
        String retrievedFact,
        String selectedModel,
        String modelName,
        long totalDurationMs,
        long modelLoadMs,
        long promptTokens,
        long generatedTokens,
        boolean cacheHit) {
}