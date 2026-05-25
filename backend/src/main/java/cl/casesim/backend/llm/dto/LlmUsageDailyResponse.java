package cl.casesim.backend.llm.dto;

import java.time.LocalDate;

public record LlmUsageDailyResponse(
        LocalDate date,
        String provider,
        String model,
        String status,
        long tokensInput,
        long tokensOutput,
        long calls,
        Double avgLatencyMs,
        Boolean tokenEstimated,
        String tokenSource
) {
}
