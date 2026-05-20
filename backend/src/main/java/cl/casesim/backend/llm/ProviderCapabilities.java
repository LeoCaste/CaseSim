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

    public static ProviderCapabilities forProvider(String provider) {
        String normalized = LlmProviderSupport.normalize(provider);
        return switch (normalized) {
            case LlmProviderSupport.OPENAI, LlmProviderSupport.GROQ, LlmProviderSupport.OPENROUTER, LlmProviderSupport.OPENAI_COMPATIBLE,
                    LlmProviderSupport.OLLAMA -> new ProviderCapabilities(true, false, true, false, true, "/chat/completions", null);
            case LlmProviderSupport.GEMINI -> new ProviderCapabilities(true, false, false, false, false, "/:generateContent", null);
            default -> new ProviderCapabilities(false, false, false, false, false, null, null);
        };
    }
}
