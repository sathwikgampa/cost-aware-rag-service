package com.example.cost_aware_rag;

import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

public class EmbeddingClient {

    private final RestClient restClient = RestClient.create("http://localhost:11434");

    public List<Double> embed(String text) {
        EmbeddingResponse response = restClient.post()
                .uri("/api/embed")
                .body(Map.of(
                        "model", "nomic-embed-text",
                        "input", text))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.embeddings().isEmpty()) {
            throw new IllegalStateException("Ollama returned no embedding.");
        }

        return response.embeddings().get(0);
    }

    private record EmbeddingResponse(List<List<Double>> embeddings) {
    }
}