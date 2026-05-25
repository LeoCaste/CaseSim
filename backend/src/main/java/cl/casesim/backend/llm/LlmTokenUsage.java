package cl.casesim.backend.llm;

public record LlmTokenUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        boolean estimated
) {
}
