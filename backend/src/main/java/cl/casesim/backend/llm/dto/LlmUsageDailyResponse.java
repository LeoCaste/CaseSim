package cl.casesim.backend.llm.dto;

import java.time.LocalDate;

public record LlmUsageDailyResponse(
        LocalDate date,
        long tokensInput,
        long tokensOutput,
        long calls,
        Double avgLatencyMs
) {
}
