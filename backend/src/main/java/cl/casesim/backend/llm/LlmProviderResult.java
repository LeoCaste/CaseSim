package cl.casesim.backend.llm;

public record LlmProviderResult(
        String provider,
        String model,
        String finalUrl,
        LlmProviderError error
) {
}
