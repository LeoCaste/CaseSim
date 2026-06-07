package cl.casesim.backend.llm;

public class LlmErrorSanitizer {

    private final LlmProperties llmProperties;

    public LlmErrorSanitizer(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    public String sanitizeError(String rawError) {
        if (rawError == null || rawError.isBlank()) {
            return "Error LLM no especificado.";
        }
        String sanitized = rawError.trim();
        if (llmProperties.getApiKey() != null && !llmProperties.getApiKey().isBlank()) {
            sanitized = sanitized.replace(llmProperties.getApiKey().trim(), "***");
        }
        sanitized = sanitized.replaceAll("(?i)bearer\\s+[a-z0-9_\\-\\.]+", "Bearer ***");
        sanitized = sanitized.replaceAll("(?i)(api[_-]?key|x-goog-api-key)\\s*[:=]\\s*[^\\s,;]+", "$1=***");
        if (sanitized.length() > 400) {
            sanitized = sanitized.substring(0, 400);
        }
        return sanitized;
    }
}
