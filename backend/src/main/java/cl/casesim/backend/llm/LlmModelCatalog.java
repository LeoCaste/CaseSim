package cl.casesim.backend.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LlmModelCatalog {

    private static final Map<String, List<String>> MODELS_BY_PROVIDER = buildModelsByProvider();

    private LlmModelCatalog() {
    }

    public static Map<String, List<String>> modelsByProvider() {
        return MODELS_BY_PROVIDER;
    }

    private static Map<String, List<String>> buildModelsByProvider() {
        Map<String, List<String>> models = new LinkedHashMap<>();
        models.put(LlmProviderSupport.OPENAI, List.of(
                "gpt-4.1-mini",
                "gpt-4o-mini",
                "gpt-4.1"
        ));
        models.put(LlmProviderSupport.GROQ, List.of(
                "llama-3.1-8b-instant",
                "llama-3.3-70b-versatile",
                "mixtral-8x7b-32768"
        ));
        models.put(LlmProviderSupport.GEMINI, List.of(
                "gemini-2.5-flash-lite",
                "gemini-2.5-flash",
                "gemini-1.5-pro"
        ));
        models.put(LlmProviderSupport.OPENROUTER, List.of(
                "openai/gpt-4.1-mini",
                "openai/gpt-4o-mini",
                "google/gemini-2.5-flash-lite",
                "meta-llama/llama-3.3-70b-instruct"
        ));
        return Map.copyOf(models);
    }
}
