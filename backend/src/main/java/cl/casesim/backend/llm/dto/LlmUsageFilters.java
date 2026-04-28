package cl.casesim.backend.llm.dto;

import java.time.LocalDate;

public record LlmUsageFilters(
        LocalDate from,
        LocalDate to,
        String model,
        LlmUsageStatusFilter status
) {
    public boolean isEmpty() {
        return from == null && to == null && model == null && status == LlmUsageStatusFilter.ALL;
    }
}
