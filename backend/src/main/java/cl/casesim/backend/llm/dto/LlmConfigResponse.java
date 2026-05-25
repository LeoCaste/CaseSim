package cl.casesim.backend.llm.dto;

import cl.casesim.backend.llm.RevealStrategy;

import java.time.LocalDateTime;

public record LlmConfigResponse(
        String provider,
        String model,
        String baseUrl,
        boolean enabled,
        boolean apiKeyConfigured,
        String maskedApiKey,
        String systemPrompt,
        String patientBehaviorRules,
        String noInfoResponse,
        RevealStrategy revealStrategy,
        int maxHistoryMessages,
        double temperature,
        int maxTokens,
        boolean enabledSafetyFilter,
        LocalDateTime updatedAt
) {
}
