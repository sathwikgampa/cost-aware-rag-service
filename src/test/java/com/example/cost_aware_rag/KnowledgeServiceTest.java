package com.example.cost_aware_rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnowledgeServiceTest {

    @Test
    void retrievesExactSemanticCachingFact() {
        KnowledgeService knowledgeService = new KnowledgeService();

        String question = "Semantic caching reuses a previous answer when a new question is close in meaning to a cached question. It uses embeddings to estimate similarity.";

        String fact = knowledgeService.findRelevantFact(question);

        assertEquals(question, fact);
    }

    @Test
    void returnsNoMatchForUnrelatedQuestion() {
        KnowledgeService knowledgeService = new KnowledgeService();

        String fact = knowledgeService.findRelevantFact(
                "What is the capital of France?");

        assertEquals("I couldn't find a matching fact.", fact);
    }
}