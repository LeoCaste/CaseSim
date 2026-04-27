package cl.casesim.backend.llm.dto;

public record LlmSummaryResponse(
        long totalCalls,
        long totalTokens,
        Double avgLatencyMs,
        long fallbackCount,
        long errorCount
) {
}
