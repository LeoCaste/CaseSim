package cl.casesim.backend.llm;

public interface LlmUsageSummaryProjection {
    long getTotalCalls();

    Long getTotalTokens();

    Long getTotalPromptTokens();

    Long getTotalCompletionTokens();

    Double getAvgLatencyMs();

    long getFallbackCount();

    long getErrorCount();
}
