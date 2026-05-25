package cl.casesim.backend.llm;

public record LlmResponse(
        String content,
        LlmUsage usage,
        LlmProviderResult providerResult
) {
}
