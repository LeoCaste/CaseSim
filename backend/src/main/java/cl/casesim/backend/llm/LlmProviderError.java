package cl.casesim.backend.llm;

public record LlmProviderError(
        LlmErrorCategory category,
        Integer httpStatus,
        String message
) {
}
