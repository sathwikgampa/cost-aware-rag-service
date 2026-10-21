package com.example.cost_aware_rag;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeService {

    private static final double MINIMUM_SIMILARITY = 0.70;
    private final EmbeddingClient embeddingClient = new EmbeddingClient();
    private final List<String> facts = loadFacts();
    private final List<List<Double>> factEmbeddings = facts.stream()
            .map(embeddingClient::embed)
            .toList();

    public String findRelevantFact(String question) {
        List<Double> questionEmbedding = embeddingClient.embed(question);

        int bestIndex = -1;
        double bestScore = -1.0;

        for (int i = 0; i < factEmbeddings.size(); i++) {
            double score = cosineSimilarity(
                    questionEmbedding,
                    factEmbeddings.get(i));

            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        // Don't return an unrelated fact just because one fact scored highest.
        if (bestIndex == -1 || bestScore < MINIMUM_SIMILARITY) {
            return "I couldn't find a matching fact.";
        }

        return facts.get(bestIndex);
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

        return dotProduct / (Math.sqrt(firstLength) * Math.sqrt(secondLength));
    }

    private List<String> loadFacts() {
        try {
            ClassPathResource resource = new ClassPathResource("knowledge/rag-notes.txt");

            String contents = resource.getContentAsString(StandardCharsets.UTF_8);

            List<String> loadedFacts = new ArrayList<>();
            for (String line : contents.split("\\R")) {
                if (!line.isBlank()) {
                    loadedFacts.add(line.trim());
                }
            }
            return loadedFacts;
        } catch (IOException e) {
            throw new IllegalStateException("Could not load rag-notes.txt", e);
        }
    }
}