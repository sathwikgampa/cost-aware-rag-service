package com.example.cost_aware_rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelRouterTest {

    private final ModelRouter modelRouter = new ModelRouter();

    @Test
    void choosesSmallForShortQuestion() {
        assertEquals("small", modelRouter.chooseModel("What is RAG?"));
    }

    @Test
    void choosesLargeWhenQuestionAsksWhy() {
        assertEquals("large", modelRouter.chooseModel("Why does RAG need retrieval?"));
    }

    @Test
    void choosesLargeForLongQuestion() {
        String question = "Explain how a RAG system retrieves relevant passages and gives them to a language model as context";

        assertEquals("large", modelRouter.chooseModel(question));
    }
}