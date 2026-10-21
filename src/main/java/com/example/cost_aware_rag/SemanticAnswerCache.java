package com.example.cost_aware_rag;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SemanticAnswerCache {

    private static final double SIMILARITY_THRESHOLD = 0.80;
    private static final String CACHE_INDEX_KEY = "rag:semantic-cache:entries";
    private static final String ENTRY_KEY_PREFIX = "rag:semantic-cache:entry:";
    private static final int MAX_ENTRIES = 500;

    private final StringRedisTemplate redisTemplate;
    private final EmbeddingClient embeddingClient = new EmbeddingClient();

    public SemanticAnswerCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public synchronized String findAnswer(String question) {
        List<Double> questionVector = embeddingClient.embed(question);

        List<String> entryIds = redisTemplate.opsForList().range(CACHE_INDEX_KEY, 0, -1);

        if (entryIds == null || entryIds.isEmpty()) {
            return null;
        }

        String bestAnswer = null;
        double bestScore = -1.0;

        for (String entryId : entryIds) {
            String entryKey = ENTRY_KEY_PREFIX + entryId;
            Map<Object, Object> entry = redisTemplate.opsForHash().entries(entryKey);

            Object savedVectorValue = entry.get("questionVector");
            Object savedAnswerValue = entry.get("answer");

            if (savedVectorValue == null || savedAnswerValue == null) {
                continue;
            }

            List<Double> savedVector = Arrays.stream(
                    savedVectorValue.toString().split(","))
                    .map(Double::valueOf)
                    .toList();

            double score = cosineSimilarity(questionVector, savedVector);

            if (score > bestScore) {
                bestScore = score;
                bestAnswer = savedAnswerValue.toString();
            }
        }

        if (bestScore < SIMILARITY_THRESHOLD) {
            return null;
        }

        return bestAnswer;
    }

    public synchronized void saveAnswer(String question, String answer) {
        String entryId = UUID.randomUUID().toString();
        String entryKey = ENTRY_KEY_PREFIX + entryId;

        String vectorText = embeddingClient.embed(question).stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        redisTemplate.opsForHash().putAll(
                entryKey,
                Map.of(
                        "questionVector", vectorText,
                        "answer", answer));

        // Keep individual entries for one day.
        redisTemplate.expire(entryKey, Duration.ofHours(24));

        redisTemplate.opsForList().rightPush(CACHE_INDEX_KEY, entryId);

        // Keep the index from growing without limit.
        redisTemplate.opsForList().trim(CACHE_INDEX_KEY, 0, MAX_ENTRIES - 1);
    }

    private double cosineSimilarity(List<Double> first, List<Double> second) {
        double dotProduct = 0.0;
        double firstLength = 0.0;
        double secondLength = 0.0;

        for (int i = 0; i < first.size(); i++) {
            dotProduct += first.get(i) * second.get(i);
            firstLength += first.get(i) * first.get(i);
            secondLength += second.get(i) * second.get(i);
        }

        if (firstLength == 0.0 || secondLength == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(firstLength) * Math.sqrt(secondLength));
    }
}