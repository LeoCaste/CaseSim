package cl.casesim.backend.llm;

public record ProviderCapabilities(
        boolean supportsSystemRole,
        boolean supportsStreaming,
        boolean supportsJsonMode,
        boolean supportsVision,
        boolean openAiCompatible,
        String defaultChatPath,
        Integer maxContextTokens
) {
}
