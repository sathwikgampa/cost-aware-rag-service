package com.example.cost_aware_rag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private static final String NO_MATCHING_FACT = "I couldn't find a matching fact.";

    private final KnowledgeService knowledgeService;
    private final ModelRouter modelRouter;
    private final OllamaClient ollamaClient;
    private final SemanticAnswerCache semanticAnswerCache;
    private final UsageMetricsService usageMetricsService;

    public HelloController(
            KnowledgeService knowledgeService,
            ModelRouter modelRouter,
            OllamaClient ollamaClient,
            SemanticAnswerCache semanticAnswerCache,
            UsageMetricsService usageMetricsService) {
        this.knowledgeService = knowledgeService;
        this.modelRouter = modelRouter;
        this.ollamaClient = ollamaClient;
        this.semanticAnswerCache = semanticAnswerCache;
        this.usageMetricsService = usageMetricsService;
    }

    @GetMapping("/api/ask")
    public AskResponse ask(@RequestParam String question) {
        long requestStartNanos = System.nanoTime();

        String cachedAnswer = semanticAnswerCache.findAnswer(question);

        if (cachedAnswer != null) {
            AskResponse response = new AskResponse(
                    cachedAnswer,
                    "Answer reused from semantic cache.",
                    "cache",
                    "semantic-cache",
                    toMillis(System.nanoTime() - requestStartNanos),
                    0,
                    0,
                    0,
                    true);

            usageMetricsService.record(response);
            return response;
        }

        String fact = knowledgeService.findRelevantFact(question);

        // If retrieval found no supporting fact, don't ask the LLM to guess.
        if (NO_MATCHING_FACT.equals(fact)) {
            AskResponse response = new AskResponse(
                    "I don't know based on the available notes.",
                    fact,
                    "none",
                    "not-called",
                    toMillis(System.nanoTime() - requestStartNanos),
                    0,
                    0,
                    0,
                    false);

            usageMetricsService.record(response);
            return response;
        }

        String selectedModel = modelRouter.chooseModel(question);

        String modelName = "large".equals(selectedModel)
                ? "qwen3:1.7b"
                : "qwen3:0.6b";

        OllamaClient.Result result = ollamaClient.generate(modelName, question, fact);

        semanticAnswerCache.saveAnswer(question, result.answer());

        AskResponse response = new AskResponse(
                result.answer(),
                fact,
                selectedModel,
                modelName,
                toMillis(System.nanoTime() - requestStartNanos),
                toMillis(result.loadDurationNanos()),
                result.promptTokens(),
                result.generatedTokens(),
                false);

        usageMetricsService.record(response);
        return response;
    }

    @GetMapping("/api/route")
    public String route(@RequestParam String question) {
        return modelRouter.chooseModel(question);
    }

    @GetMapping("/api/metrics")
    public UsageMetricsService.Snapshot metrics() {
        return usageMetricsService.getSnapshot();
    }

    private long toMillis(long nanoseconds) {
        return nanoseconds < 0 ? -1 : nanoseconds / 1_000_000;
    }
}