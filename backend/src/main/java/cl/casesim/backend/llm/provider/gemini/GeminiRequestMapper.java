package cl.casesim.backend.llm.provider.gemini;

import cl.casesim.backend.llm.LlmMessage;
import cl.casesim.backend.llm.LlmProperties;
import cl.casesim.backend.llm.LlmRequest;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

public class GeminiRequestMapper {

    public Map<String, Object> toGenerateContentPayload(LlmRequest request, LlmProperties properties) {
        double temperature = request.temperature() == null ? properties.getTemperature() : request.temperature();
        int maxOutputTokens = request.maxTokens() == null ? properties.getMaxTokens() : request.maxTokens();

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("contents", mapContents(request.messages()));
        payload.put("generationConfig", Map.of(
                "temperature", temperature,
                "maxOutputTokens", maxOutputTokens
        ));

        String systemPrompt = buildSystemInstructionText(request.messages());
        if (StringUtils.hasText(systemPrompt)) {
            payload.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt))
            ));
        }
        return payload;
    }

    private List<Map<String, Object>> mapContents(List<LlmMessage> messages) {
        if (messages == null) {
            return List.of();
        }
        return messages.stream()
                .filter(msg -> "user".equalsIgnoreCase(msg.role()) || "assistant".equalsIgnoreCase(msg.role()))
                .map(msg -> Map.<String, Object>of(
                        "role", "assistant".equalsIgnoreCase(msg.role()) ? "model" : "user",
                        "parts", List.of(Map.of("text", msg.content()))
                ))
                .toList();
    }

    private String buildSystemInstructionText(List<LlmMessage> messages) {
        if (messages == null) {
            return "";
        }
        return messages.stream()
                .filter(msg -> "system".equalsIgnoreCase(msg.role()))
                .map(LlmMessage::content)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }
}
