package cl.casesim.backend.llm.dto;

import java.util.List;

public record LlmProviderModelsResponse(
        String provider,
        List<String> models
) {
}
