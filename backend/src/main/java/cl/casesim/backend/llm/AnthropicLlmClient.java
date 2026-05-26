package cl.casesim.backend.llm;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

public class AnthropicLlmClient implements LlmClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final LlmProperties llmProperties;
    private final LlmProviderUrlResolver urlResolver;
    private final LlmProviderErrorMapper errorMapper;

    public AnthropicLlmClient(LlmProperties llmProperties, LlmProviderUrlResolver urlResolver, LlmProviderErrorMapper errorMapper) {
        this.llmProperties = llmProperties;
        this.urlResolver = urlResolver;
        this.errorMapper = errorMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(llmProperties.getTimeoutMs());
        requestFactory.setReadTimeout(llmProperties.getTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String providerType() {
        return LlmProviderSupport.ANTHROPIC;
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        String provider = LlmProviderSupport.normalize(llmProperties.getProvider());
        if (!LlmProviderSupport.ANTHROPIC.equals(provider)) {
            throw new LlmClientException("Proveedor no implementado: " + provider);
        }
        if (!StringUtils.hasText(llmProperties.getApiKey())) {
            throw new LlmClientException("API key no configurada.");
        }

        String resolvedModel = StringUtils.hasText(request.model()) ? request.model().trim() : llmProperties.getModel();
        if (StringUtils.hasText(resolvedModel) && resolvedModel.toLowerCase().startsWith("anthropic/")) {
            throw new LlmClientException(
                    "Modelo inválido para provider anthropic: use modelo nativo sin prefijo 'anthropic/' (ej: claude-sonnet-4-5).",
                    null,
                    new LlmProviderError(LlmErrorCategory.INVALID_REQUEST, 400, "anthropic_prefixed_model")
            );
        }

        int attempts = Math.max(1, llmProperties.getMaxRetries() + 1);
        String resolvedUrl = urlResolver.resolve(provider, llmProperties.getBaseUrl());
        String requestPath = "/v1/messages";

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = executeRequest(request, resolvedUrl);

                String content = extractAnthropicContent(response);
                if (!StringUtils.hasText(content)) {
                    throw new LlmClientException("Proveedor LLM devolvió respuesta vacía o no parseable.");
                }

                return new LlmResponse(content, extractUsage(response), new LlmProviderResult(provider, resolvedModel, resolvedUrl, null));
            } catch (RestClientException ex) {
                if (ex instanceof ResourceAccessException) {
                    if (attempt == attempts) {
                        LlmProviderError pe = new LlmProviderError(LlmErrorCategory.PROVIDER_UNAVAILABLE, null, "timeout");
                        throw new LlmClientException("Timeout al invocar proveedor LLM", ex, pe);
                    }
                    continue;
                }
                if (ex instanceof RestClientResponseException responseException) {
                    int status = responseException.getStatusCode().value();
                    LlmProviderError providerError = errorMapper.map(status, responseException.getResponseBodyAsString());
                    String message = buildHttpErrorMessage(status, responseException.getResponseBodyAsString(), requestPath);
                    if (attempt == attempts) {
                        throw new LlmClientException(message, ex, providerError);
                    }
                    continue;
                }
                if (attempt == attempts) {
                    throw new LlmClientException("Error invocando proveedor LLM", ex,
                            new LlmProviderError(LlmErrorCategory.UNKNOWN, null, "runtime_error"));
                }
            }
        }

        throw new LlmClientException("No fue posible obtener respuesta del proveedor LLM");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeRequest(LlmRequest request, String resolvedUrl) {
        return restClient.post()
                .uri(resolvedUrl)
                .header("x-api-key", llmProperties.getApiKey().trim())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildAnthropicPayload(request))
                .retrieve()
                .body(Map.class);
    }

    Map<String, Object> buildAnthropicPayload(LlmRequest request) {
        double resolvedTemperature = request.temperature() == null ? llmProperties.getTemperature() : request.temperature();
        int resolvedMaxTokens = request.maxTokens() == null ? llmProperties.getMaxTokens() : request.maxTokens();
        String resolvedModel = StringUtils.hasText(request.model()) ? request.model().trim() : llmProperties.getModel();
        List<LlmMessage> messages = request.messages() == null ? List.of() : request.messages();

        return Map.of(
                "model", resolvedModel,
                "messages", messages.stream()
                        .filter(message -> "user".equalsIgnoreCase(message.role()) || "assistant".equalsIgnoreCase(message.role()))
                        .map(message -> Map.of("role", message.role().toLowerCase(), "content", message.content()))
                        .toList(),
                "system", buildSystemPrompt(messages),
                "temperature", resolvedTemperature,
                "max_tokens", resolvedMaxTokens
        );
    }

    String extractAnthropicContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }

        Object contentObj = response.get("content");
        if (!(contentObj instanceof List<?> contentItems) || contentItems.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (Object item : contentItems) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                continue;
            }
            Object typeObj = itemMap.get("type");
            if (!(typeObj instanceof String type) || !"text".equalsIgnoreCase(type)) {
                continue;
            }
            Object textObj = itemMap.get("text");
            if (textObj instanceof String text && StringUtils.hasText(text)) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(text.trim());
            }
        }
        return builder.toString();
    }

    private String buildSystemPrompt(List<LlmMessage> messages) {
        return messages.stream()
                .filter(message -> "system".equalsIgnoreCase(message.role()))
                .map(LlmMessage::content)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    private LlmTokenUsage extractUsage(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object usageObj = response.get("usage");
        if (!(usageObj instanceof Map<?, ?> usage)) {
            return null;
        }
        Integer prompt = toInteger(usage.get("input_tokens"));
        Integer completion = toInteger(usage.get("output_tokens"));
        Integer total = toInteger(usage.get("total_tokens"));
        if (total == null && prompt != null && completion != null) {
            total = prompt + completion;
        }
        if (prompt == null && completion == null && total == null) {
            return null;
        }
        return new LlmTokenUsage(prompt, completion, total, false);
    }

    private Integer toInteger(Object value) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private String buildHttpErrorMessage(int status, String body, String requestPath) {
        String category = errorMapper.map(status, body).category().name();
        String sanitizedBody = sanitizeProviderBody(body);
        String suffix = StringUtils.hasText(sanitizedBody) ? " detail=" + sanitizedBody : "";
        return "Error HTTP proveedor LLM status=" + status + " category=" + category + " path=" + requestPath + suffix;
    }

    private String sanitizeProviderBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        String sanitized = body;
        String apiKey = llmProperties.getApiKey();
        if (StringUtils.hasText(apiKey)) {
            sanitized = sanitized.replace(apiKey.trim(), "***");
        }
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized.length() > 240 ? sanitized.substring(0, 240) + "..." : sanitized;
    }
}
