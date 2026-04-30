package cl.casesim.backend.llm;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        return generateChatCompletion(messages, llmProperties.getTemperature(), llmProperties.getMaxTokens());
    }

    @Override
    public String generateChatCompletion(List<ChatPromptMessage> messages, Double temperature, Integer maxTokens) {
        String provider = LlmProviderSupport.normalize(llmProperties.getProvider());
        if (!LlmProviderSupport.isSupported(provider)) {
            throw new LlmClientException("Proveedor no implementado: " + provider);
        }

        if (!StringUtils.hasText(llmProperties.getApiKey())) {
            throw new LlmClientException("API key no configurada.");
        }

        int attempts = Math.max(1, llmProperties.getMaxRetries() + 1);

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = executeProviderRequest(provider, messages, temperature, maxTokens);

                return extractContent(provider, response);
            } catch (RestClientException ex) {
                if (ex instanceof ResourceAccessException) {
                    throw new LlmClientException("Timeout al invocar proveedor LLM", ex);
                }
                if (attempt == attempts) {
                    throw new LlmClientException("Error invocando proveedor LLM", ex);
                }
            }
        }

        throw new LlmClientException("No fue posible obtener respuesta del proveedor LLM");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeProviderRequest(
            String provider,
            List<ChatPromptMessage> messages,
            Double temperature,
            Integer maxTokens
    ) {
        return switch (provider) {
            case LlmProviderSupport.OPENAI, LlmProviderSupport.OPENAI_COMPATIBLE, LlmProviderSupport.GROQ -> restClient.post()
                    .uri(resolveOpenAiCompatibleUrl(provider))
                    .header("Authorization", "Bearer " + llmProperties.getApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildOpenAiCompatiblePayload(messages, temperature, maxTokens))
                    .retrieve()
                    .body(Map.class);
            case LlmProviderSupport.ANTHROPIC -> restClient.post()
                    .uri(resolveAnthropicUrl())
                    .header("x-api-key", llmProperties.getApiKey().trim())
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildAnthropicPayload(messages, temperature, maxTokens))
                    .retrieve()
                    .body(Map.class);
            case LlmProviderSupport.GEMINI -> restClient.post()
                    .uri(resolveGeminiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildGeminiPayload(messages, temperature, maxTokens))
                    .retrieve()
                    .body(Map.class);
            default -> throw new LlmClientException("Proveedor no implementado: " + provider);
        };
    }

    private Map<String, Object> buildOpenAiCompatiblePayload(List<ChatPromptMessage> messages, Double temperature, Integer maxTokens) {
        double resolvedTemperature = temperature == null ? llmProperties.getTemperature() : temperature;
        int resolvedMaxTokens = maxTokens == null ? llmProperties.getMaxTokens() : maxTokens;
        return Map.of(
                "model", llmProperties.getModel(),
                "messages", messages,
                "temperature", resolvedTemperature,
                "max_tokens", resolvedMaxTokens
        );
    }

    private Map<String, Object> buildAnthropicPayload(List<ChatPromptMessage> messages, Double temperature, Integer maxTokens) {
        double resolvedTemperature = temperature == null ? llmProperties.getTemperature() : temperature;
        int resolvedMaxTokens = maxTokens == null ? llmProperties.getMaxTokens() : maxTokens;
        return Map.of(
                "model", llmProperties.getModel(),
                "messages", messages.stream()
                        .filter(message -> "user".equalsIgnoreCase(message.role()) || "assistant".equalsIgnoreCase(message.role()))
                        .map(message -> Map.of("role", message.role().toLowerCase(), "content", message.content()))
                        .toList(),
                "system", buildSystemPrompt(messages),
                "temperature", resolvedTemperature,
                "max_tokens", resolvedMaxTokens
        );
    }

    private Map<String, Object> buildGeminiPayload(List<ChatPromptMessage> messages, Double temperature, Integer maxTokens) {
        double resolvedTemperature = temperature == null ? llmProperties.getTemperature() : temperature;
        int resolvedMaxTokens = maxTokens == null ? llmProperties.getMaxTokens() : maxTokens;
        return Map.of(
                "contents", messages.stream()
                        .filter(message -> "user".equalsIgnoreCase(message.role()) || "assistant".equalsIgnoreCase(message.role()))
                        .map(message -> Map.of(
                                "role", "assistant".equalsIgnoreCase(message.role()) ? "model" : "user",
                                "parts", List.of(Map.of("text", message.content()))
                        ))
                        .toList(),
                "systemInstruction", Map.of("parts", List.of(Map.of("text", buildSystemPrompt(messages)))),
                "generationConfig", Map.of(
                        "temperature", resolvedTemperature,
                        "maxOutputTokens", resolvedMaxTokens
                )
        );
    }

    private String buildSystemPrompt(List<ChatPromptMessage> messages) {
        return messages.stream()
                .filter(message -> "system".equalsIgnoreCase(message.role()))
                .map(ChatPromptMessage::content)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    private String extractContent(String provider, Map<String, Object> response) {
        return switch (provider) {
            case LlmProviderSupport.ANTHROPIC -> extractAnthropicContent(response);
            case LlmProviderSupport.GEMINI -> extractGeminiContent(response);
            default -> extractOpenAiCompatibleContent(response);
        };
    }

    @SuppressWarnings("unchecked")
    private String extractOpenAiCompatibleContent(Map<String, Object> response) {
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

    @SuppressWarnings("unchecked")
    private String extractAnthropicContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object contentObj = response.get("content");
        if (!(contentObj instanceof List<?> contentItems) || contentItems.isEmpty()) {
            return "";
        }
        Object first = contentItems.getFirst();
        if (!(first instanceof Map<?, ?> firstMap)) {
            return "";
        }
        Object text = firstMap.get("text");
        return text instanceof String contentText && StringUtils.hasText(contentText) ? contentText.trim() : "";
    }

    @SuppressWarnings("unchecked")
    private String extractGeminiContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object candidatesObj = response.get("candidates");
        if (!(candidatesObj instanceof List<?> candidates) || candidates.isEmpty()) {
            return "";
        }
        Object firstCandidate = candidates.getFirst();
        if (!(firstCandidate instanceof Map<?, ?> candidateMap)) {
            return "";
        }
        Object contentObj = candidateMap.get("content");
        if (!(contentObj instanceof Map<?, ?> contentMap)) {
            return "";
        }
        Object partsObj = contentMap.get("parts");
        if (!(partsObj instanceof List<?> parts) || parts.isEmpty()) {
            return "";
        }
        Object firstPart = parts.getFirst();
        if (!(firstPart instanceof Map<?, ?> partMap)) {
            return "";
        }
        Object textObj = partMap.get("text");
        return textObj instanceof String text && StringUtils.hasText(text) ? text.trim() : "";
    }

    private String resolveOpenAiCompatibleUrl(String provider) {
        return LlmProviderSupport.resolveBaseUrl(provider, llmProperties.getBaseUrl());
    }

    private String resolveAnthropicUrl() {
        return LlmProviderSupport.resolveBaseUrl(LlmProviderSupport.ANTHROPIC, llmProperties.getBaseUrl());
    }

    private String resolveGeminiUrl() {
        String configured = LlmProviderSupport.resolveBaseUrl(LlmProviderSupport.GEMINI, llmProperties.getBaseUrl());
        String base = configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        String model = StringUtils.hasText(llmProperties.getModel()) ? llmProperties.getModel().trim() : "gemini-1.5-flash";
        String encodedKey = URLEncoder.encode(llmProperties.getApiKey().trim(), StandardCharsets.UTF_8);
        return base + "/" + model + ":generateContent?key=" + encodedKey;
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
