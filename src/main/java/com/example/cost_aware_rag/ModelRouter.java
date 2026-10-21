package com.example.cost_aware_rag;

import org.springframework.stereotype.Service;

@Service
public class ModelRouter {

    public String chooseModel(String question) {
        String normalizedQuestion = question.toLowerCase().trim();
        int wordCount = normalizedQuestion.split("\\s+").length;

        boolean asksForDeeperReasoning = normalizedQuestion.contains("compare")
                || normalizedQuestion.contains("step by step")
                || normalizedQuestion.contains("why");

        if (wordCount > 12 || asksForDeeperReasoning) {
            return "large";
        }

        return "small";
    }
}