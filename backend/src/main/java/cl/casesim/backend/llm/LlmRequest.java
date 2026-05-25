package cl.casesim.backend.llm;

import java.util.List;

public record LlmRequest(
        List<LlmMessage> messages,
        String model,
        Double temperature,
        Integer maxTokens
) {
}
