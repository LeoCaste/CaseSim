package cl.casesim.backend.llm.dto;

import java.time.LocalDateTime;

public record LlmConfigResponse(
        String provider,
        String model,
        String baseUrl,
        boolean enabled,
        boolean apiKeyConfigured,
        String maskedApiKey,
        LocalDateTime updatedAt
) {
}
