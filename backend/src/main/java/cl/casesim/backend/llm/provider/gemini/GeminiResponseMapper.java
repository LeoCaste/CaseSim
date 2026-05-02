package cl.casesim.backend.llm.provider.gemini;

import cl.casesim.backend.llm.LlmClientException;
import cl.casesim.backend.llm.LlmErrorCategory;
import cl.casesim.backend.llm.LlmProviderError;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

public class GeminiResponseMapper {

    public String extractContent(Map<String, Object> response) {
        String parsed = parseCandidatesText(response);
        if (!StringUtils.hasText(parsed)) {
            throw new LlmClientException(
                    "Proveedor Gemini devolvió respuesta vacía o no parseable.",
                    null,
                    new LlmProviderError(LlmErrorCategory.INVALID_RESPONSE, null, "empty_response")
            );
        }
        return parsed;
    }

    public Integer promptTokens(Map<String, Object> response) {
        return usageValue(response, "promptTokenCount");
    }

    public Integer completionTokens(Map<String, Object> response) {
        return usageValue(response, "candidatesTokenCount");
    }

    public Integer totalTokens(Map<String, Object> response) {
        return usageValue(response, "totalTokenCount");
    }

    @SuppressWarnings("unchecked")
    private String parseCandidatesText(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object candidatesObj = response.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) {
            return "";
        }

        StringBuilder output = new StringBuilder();
        for (Object candidateObj : candidates) {
            if (!(candidateObj instanceof Map<?, ?> candidateMap)) {
                continue;
            }
            Object contentObj = candidateMap.get("content");
            if (!(contentObj instanceof Map<?, ?> contentMap)) {
                continue;
            }
            Object partsObj = contentMap.get("parts");
            if (!(partsObj instanceof List<?> parts)) {
                continue;
            }
            for (Object partObj : parts) {
                if (!(partObj instanceof Map<?, ?> partMap)) {
                    continue;
                }
                Object textObj = partMap.get("text");
                if (textObj instanceof String text && StringUtils.hasText(text)) {
                    if (!output.isEmpty()) {
                        output.append('\n');
                    }
                    output.append(text.trim());
                }
            }
        }
        return output.toString();
    }

    @SuppressWarnings("unchecked")
    private Integer usageValue(Map<String, Object> response, String key) {
        if (response == null) {
            return null;
        }
        Object usageObj = response.get("usageMetadata");
        if (!(usageObj instanceof Map<?, ?> usage)) {
            return null;
        }
        Object value = usage.get(key);
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
