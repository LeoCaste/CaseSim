package cl.casesim.backend.llm;

public interface LlmUsageSummaryProjection {
    long getTotalCalls();

    Long getTotalTokens();

    Double getAvgLatencyMs();

    long getFallbackCount();

    long getErrorCount();
}
