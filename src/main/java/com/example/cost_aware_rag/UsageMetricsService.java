package com.example.cost_aware_rag;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.LongAdder;

@Service
public class UsageMetricsService {

    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder smallModelCalls = new LongAdder();
    private final LongAdder largeModelCalls = new LongAdder();
    private final LongAdder totalPromptTokens = new LongAdder();
    private final LongAdder totalGeneratedTokens = new LongAdder();
    private final LongAdder totalLatencyMs = new LongAdder();

    public void record(AskResponse response) {
        totalRequests.increment();
        totalLatencyMs.add(response.totalDurationMs());

        if (response.cacheHit()) {
            cacheHits.increment();
            return;
        }

        if ("small".equals(response.selectedModel())) {
            smallModelCalls.increment();
        } else if ("large".equals(response.selectedModel())) {
            largeModelCalls.increment();
        }

        totalPromptTokens.add(response.promptTokens());
        totalGeneratedTokens.add(response.generatedTokens());
    }

    public Snapshot getSnapshot() {
        long requests = totalRequests.sum();
        long hits = cacheHits.sum();

        double averageLatencyMs = requests == 0
                ? 0.0
                : (double) totalLatencyMs.sum() / requests;

        double cacheHitRatePercent = requests == 0
                ? 0.0
                : (double) hits * 100.0 / requests;

        return new Snapshot(
                requests,
                hits,
                requests - hits,
                smallModelCalls.sum(),
                largeModelCalls.sum(),
                totalPromptTokens.sum(),
                totalGeneratedTokens.sum(),
                averageLatencyMs,
                cacheHitRatePercent);
    }

    public record Snapshot(
            long totalRequests,
            long cacheHits,
            long cacheMisses,
            long smallModelCalls,
            long largeModelCalls,
            long totalPromptTokens,
            long totalGeneratedTokens,
            double averageLatencyMs,
            double cacheHitRatePercent) {
    }
}