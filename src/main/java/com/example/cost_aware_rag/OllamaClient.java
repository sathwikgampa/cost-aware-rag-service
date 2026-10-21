package com.example.cost_aware_rag;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class OllamaClient {

    private final RestClient restClient = RestClient.create("http://localhost:11434");

    public Result generate(String model, String question, String context) {
        String systemMessage = "Answer the question directly in one concise sentence, using only the supplied context. "
                + "If you answer yes or no, make sure that word agrees with the rest of your sentence. "
                + "If the context does not support an answer, say: I don't know based on the supplied context.";

        String userMessage = "Context:\n" + context + "\n\nQuestion:\n" + question;

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemMessage),
                        Map.of("role", "user", "content", userMessage)),
                "think", false,
                "stream", false);

        Map<String, Object> response = restClient.post()
                .uri("/api/chat")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        if (response == null || !(response.get("message") instanceof Map<?, ?> message)) {
            throw new IllegalStateException("Ollama returned an unexpected response.");
        }

        String answer = String.valueOf(message.get("content"));

        return new Result(
                answer,
                number(response.get("total_duration")),
                number(response.get("load_duration")),
                number(response.get("prompt_eval_count")),
                number(response.get("eval_count")));
    }

    private long number(Object value) {
        return value instanceof Number n ? n.longValue() : -1;
    }

    public record Result(
            String answer,
            long totalDurationNanos,
            long loadDurationNanos,
            long promptTokens,
            long generatedTokens) {
    }
}