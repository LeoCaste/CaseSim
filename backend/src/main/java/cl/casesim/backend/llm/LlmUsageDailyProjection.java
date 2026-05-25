package cl.casesim.backend.llm;

import java.time.LocalDate;

public interface LlmUsageDailyProjection {
    LocalDate getUsageDate();

    long getTokensInput();

    long getTokensOutput();

    long getCalls();

    Double getAvgLatencyMs();
}
