package cl.casesim.backend.llm;

public record LlmResponse(
        String content,
        LlmTokenUsage usage,
        LlmProviderResult providerResult
) {
}
