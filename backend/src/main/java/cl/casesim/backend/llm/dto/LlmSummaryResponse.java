package cl.casesim.backend.llm.dto;

import java.math.BigDecimal;

public record LlmSummaryResponse(
        long totalCalls,
        long totalTokens,
        Double avgLatencyMs,
        long fallbackCount,
        long errorCount,
        BigDecimal estimatedCostUsd,
        BigDecimal estimatedCostClp,
        BigDecimal usdToClpRate
) {
}
