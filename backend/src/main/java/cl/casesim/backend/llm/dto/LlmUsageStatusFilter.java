package cl.casesim.backend.llm.dto;

import cl.casesim.backend.common.exception.BadRequestException;

import java.util.Locale;

public enum LlmUsageStatusFilter {
    ALL("all"),
    ERROR("error"),
    FALLBACK("fallback");

    private final String dbValue;

    LlmUsageStatusFilter(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static LlmUsageStatusFilter fromNullable(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return ALL;
        }

        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
        for (LlmUsageStatusFilter value : values()) {
            if (value.dbValue.equals(normalized)) {
                return value;
            }
        }

        throw new BadRequestException("El parámetro status debe ser all, error o fallback.");
    }
}
