package cl.casesim.backend.llm;

import java.time.LocalDate;

public interface LlmUsageDailyProjection {
    LocalDate getUsageDate();

    String getProvider();

    String getModel();

    String getStatus();

    long getTokensInput();

    long getTokensOutput();

    long getCalls();

    Double getAvgLatencyMs();

    Boolean getTokenEstimated();

    String getTokenSource();
}
