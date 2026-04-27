package cl.casesim.backend.llm;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

public class OpenAiLlmClient implements LlmClient {

    private final RestClient restClient;
    private final LlmProperties llmProperties;

    public OpenAiLlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(llmProperties.getTimeoutMs());
        requestFactory.setReadTimeout(llmProperties.getTimeoutMs());

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String generateChatCompletion(List<ChatPromptMessage> messages) {
        int attempts = Math.max(1, llmProperties.getMaxRetries() + 1);

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restClient.post()
                        .uri(llmProperties.getBaseUrl())
                        .header("Authorization", "Bearer " + llmProperties.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(buildPayload(messages))
                        .retrieve()
                        .body(Map.class);

                return extractContent(response);
            } catch (RestClientException ex) {
                if (attempt == attempts) {
                    throw new LlmClientException("Error invocando proveedor LLM", ex);
                }
            }
        }

        throw new LlmClientException("No fue posible obtener respuesta del proveedor LLM");
    }

    private Map<String, Object> buildPayload(List<ChatPromptMessage> messages) {
        return Map.of(
                "model", llmProperties.getModel(),
                "messages", messages,
                "temperature", llmProperties.getTemperature(),
                "max_tokens", llmProperties.getMaxTokens()
        );
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }

        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }

        Object firstChoice = choices.getFirst();
        if (!(firstChoice instanceof Map<?, ?> firstChoiceMap)) {
            return "";
        }

        Object messageObj = firstChoiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            return "";
        }

        Object contentObj = messageMap.get("content");
        String content = contentObj instanceof String contentText ? contentText : "";
        return StringUtils.hasText(content) ? content.trim() : "";
    }

    public static class LlmClientException extends RuntimeException {
        public LlmClientException(String message) {
            super(message);
        }

        public LlmClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
