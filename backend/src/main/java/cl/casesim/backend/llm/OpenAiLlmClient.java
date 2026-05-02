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

public class OpenAiLlmClient implements LlmClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final RestClient restClient;
    private final LlmProperties llmProperties;
    private final LlmProviderUrlResolver urlResolver;
    private final LlmProviderErrorMapper errorMapper;

    public OpenAiLlmClient(LlmProperties llmProperties, LlmProviderUrlResolver urlResolver, LlmProviderErrorMapper errorMapper) {
        this.llmProperties = llmProperties;
        this.urlResolver = urlResolver;
        this.errorMapper = errorMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(llmProperties.getTimeoutMs());
        requestFactory.setReadTimeout(llmProperties.getTimeoutMs());

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String providerType() {
        return LlmProviderSupport.OPENAI;
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        List<LlmMessage> messages = request.messages();
        Double temperature = request.temperature();
        Integer maxTokens = request.maxTokens();
        String provider = LlmProviderSupport.normalize(llmProperties.getProvider());
        if (!LlmProviderSupport.isSupported(provider)) {
            throw new cl.casesim.backend.llm.LlmClientException("Proveedor no implementado: " + provider);
        }

        if (!StringUtils.hasText(llmProperties.getApiKey())) {
            throw new cl.casesim.backend.llm.LlmClientException("API key no configurada.");
        }

        int attempts = Math.max(1, llmProperties.getMaxRetries() + 1);
        String normalizedBaseUrl = resolveOpenAiCompatibleBaseUrl(provider);
        String finalPath = urlResolver.resolveChatCompletionsPath(provider);
        String resolvedUrl = resolveOpenAiCompatibleUrl(provider);
        boolean customBaseUrl = StringUtils.hasText(llmProperties.getBaseUrl());
        String requestMode = resolveOpenAiRequestMode(provider, finalPath);

        log.info("LLM client request provider={} model={} mode={} normalizedBaseUrl={} finalPath={} customBaseUrl={} messagesCount={} temperature={} maxTokens={}",
                provider,
                llmProperties.getModel(),
                requestMode,
                normalizedBaseUrl,
                finalPath,
                customBaseUrl,
                messages == null ? 0 : messages.size(),
                temperature == null ? llmProperties.getTemperature() : temperature,
                maxTokens == null ? llmProperties.getMaxTokens() : maxTokens
        );

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = executeProviderRequest(provider, resolvedUrl, messages, temperature, maxTokens, requestMode);
                log.debug("LLM client response shape provider={} mode={} keys={}",
                        provider,
                        requestMode,
                        response == null ? List.of() : response.keySet());
                String content = extractContent(provider, response);
                if (!StringUtils.hasText(content)) {
                    throw new cl.casesim.backend.llm.LlmClientException("Proveedor LLM devolvió respuesta vacía o no parseable.");
                }
                log.info("LLM client parsed response provider={} mode={} textLength={}",
                        provider,
                        requestMode,
                        content.length());
                return new LlmResponse(
                        content,
                        null,
                        new LlmProviderResult(provider, llmProperties.getModel(), resolvedUrl, null)
                );
            } catch (RestClientException ex) {
                if (ex instanceof ResourceAccessException) {
                    if (attempt == attempts) {
                        LlmProviderError pe = new LlmProviderError(LlmErrorCategory.TIMEOUT, null, "timeout");
                        throw new cl.casesim.backend.llm.LlmClientException("Timeout al invocar proveedor LLM", ex, pe);
                    }
                    continue;
                }
                if (ex instanceof RestClientResponseException responseException) {
                    LlmProviderError providerError = errorMapper.map(responseException.getStatusCode().value(), responseException.getResponseBodyAsString());
                    String message = buildHttpErrorMessage(responseException, finalPath);
                    if (attempt == attempts) {
                        throw new cl.casesim.backend.llm.LlmClientException(message, ex, providerError);
                    }
                    continue;
                }
                if (attempt == attempts) {
                    throw new cl.casesim.backend.llm.LlmClientException("Error invocando proveedor LLM", ex, new LlmProviderError(LlmErrorCategory.UNKNOWN, null, "runtime_error"));
                }
            }
        }

        throw new cl.casesim.backend.llm.LlmClientException("No fue posible obtener respuesta del proveedor LLM");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeProviderRequest(
            String provider,
            String resolvedUrl,
            List<LlmMessage> messages,
            Double temperature,
            Integer maxTokens,
            String requestMode
    ) {
        return switch (provider) {
            case LlmProviderSupport.OPENAI, LlmProviderSupport.OPENAI_COMPATIBLE -> restClient.post()
                    .uri(resolvedUrl)
                    .header("Authorization", "Bearer " + llmProperties.getApiKey().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildOpenAiPayload(messages, temperature, maxTokens, requestMode))
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
            default -> throw new cl.casesim.backend.llm.LlmClientException("Proveedor no implementado: " + provider);
        };
    }

    private Map<String, Object> buildOpenAiPayload(List<LlmMessage> messages, Double temperature, Integer maxTokens, String requestMode) {
        if ("responses".equals(requestMode)) {
            return buildOpenAiResponsesPayload(messages, temperature, maxTokens);
        }
        return buildOpenAiCompatiblePayload(messages, temperature, maxTokens);
    }

    private Map<String, Object> buildOpenAiCompatiblePayload(List<LlmMessage> messages, Double temperature, Integer maxTokens) {
        double resolvedTemperature = temperature == null ? llmProperties.getTemperature() : temperature;
        int resolvedMaxTokens = maxTokens == null ? llmProperties.getMaxTokens() : maxTokens;
        return Map.of(
                "model", llmProperties.getModel(),
                "messages", messages,
                "temperature", resolvedTemperature,
                "max_tokens", resolvedMaxTokens
        );
    }

    private Map<String, Object> buildOpenAiResponsesPayload(List<LlmMessage> messages, Double temperature, Integer maxTokens) {
        double resolvedTemperature = temperature == null ? llmProperties.getTemperature() : temperature;
        int resolvedMaxTokens = maxTokens == null ? llmProperties.getMaxTokens() : maxTokens;
        return Map.of(
                "model", llmProperties.getModel(),
                "input", messages.stream()
                        .map(message -> Map.of(
                                "role", message.role(),
                                "content", List.of(Map.of("type", "input_text", "text", message.content()))
                        ))
                        .toList(),
                "temperature", resolvedTemperature,
                "max_output_tokens", resolvedMaxTokens
        );
    }

    private Map<String, Object> buildAnthropicPayload(List<LlmMessage> messages, Double temperature, Integer maxTokens) {
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
    private String extractContent(String provider, Map<String, Object> response) {
        return switch (provider) {
            case LlmProviderSupport.ANTHROPIC -> extractAnthropicContent(response);
            default -> extractOpenAiCompatibleContent(response);
        };
    }

    @SuppressWarnings("unchecked")
    private String extractOpenAiCompatibleContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }

        String responsesApiContent = extractOpenAiResponsesContent(response);
        if (StringUtils.hasText(responsesApiContent)) {
            return responsesApiContent;
        }

        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }

        Object firstChoice = choices.getFirst();
        if (!(firstChoice instanceof Map<?, ?> firstChoiceMap)) {
            return "";
        }

        Object legacyText = firstChoiceMap.get("text");
        if (legacyText instanceof String legacy && StringUtils.hasText(legacy)) {
            return legacy.trim();
        }

        Object messageObj = firstChoiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            return "";
        }

        Object contentObj = messageMap.get("content");
        String content = switch (contentObj) {
            case String contentText -> contentText;
            case List<?> contentParts -> extractTextFromContentParts(contentParts);
            default -> "";
        };
        return StringUtils.hasText(content) ? content.trim() : "";
    }

    @SuppressWarnings("unchecked")
    String extractOpenAiResponsesContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }

        Object outputTextObj = response.get("output_text");
        if (outputTextObj instanceof String outputText && StringUtils.hasText(outputText)) {
            return outputText.trim();
        }

        Object outputObj = response.get("output");
        if (!(outputObj instanceof List<?> outputItems) || outputItems.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (Object outputItem : outputItems) {
            if (!(outputItem instanceof Map<?, ?> outputMap)) {
                continue;
            }
            Object contentObj = outputMap.get("content");
            if (!(contentObj instanceof List<?> contentItems)) {
                continue;
            }
            for (Object contentItem : contentItems) {
                if (!(contentItem instanceof Map<?, ?> contentMap)) {
                    continue;
                }
                Object textObj = contentMap.get("text");
                if (textObj instanceof String text && StringUtils.hasText(text)) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text.trim());
                }
            }
        }
        return builder.toString();
    }

    private String extractTextFromContentParts(List<?> contentParts) {
        StringBuilder builder = new StringBuilder();
        for (Object part : contentParts) {
            if (!(part instanceof Map<?, ?> partMap)) {
                continue;
            }
            Object textObj = partMap.get("text");
            if (textObj instanceof String text && StringUtils.hasText(text)) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(text.trim());
            }
        }
        return builder.toString();
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
        if (text instanceof String contentText && StringUtils.hasText(contentText)) {
            return contentText.trim();
        }
        return contentItems.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(item -> item.get("text"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String resolveOpenAiCompatibleUrl(String provider) {
        return urlResolver.resolve(provider, llmProperties.getBaseUrl());
    }

    private String resolveOpenAiCompatibleBaseUrl(String provider) {
        return urlResolver.resolveBaseUrl(provider, llmProperties.getBaseUrl());
    }

    private String resolveOpenAiRequestMode(String provider, String finalPath) {
        if (!(LlmProviderSupport.OPENAI.equals(provider) || LlmProviderSupport.OPENAI_COMPATIBLE.equals(provider))) {
            return "chat_completions";
        }
        return finalPath.contains("/responses") ? "responses" : "chat_completions";
    }

    private String resolveAnthropicUrl() {
        return urlResolver.resolve(LlmProviderSupport.ANTHROPIC, llmProperties.getBaseUrl());
    }

    private String buildHttpErrorMessage(RestClientResponseException responseException, String requestPath) {
        int status = responseException.getStatusCode().value();
        String body = responseException.getResponseBodyAsString();
        String bodyLower = body == null ? "" : body.toLowerCase();

        String category;
        if (status == 400 && bodyLower.contains("model") && (bodyLower.contains("invalid") || bodyLower.contains("does not exist") || bodyLower.contains("not found"))) {
            category = "MODEL_INVALID";
        } else if (status == 400 && (bodyLower.contains("messages") || bodyLower.contains("input"))) {
            category = "PAYLOAD_INVALID";
        } else if (status == 401 || status == 403) {
            category = "AUTH_ERROR";
        } else if (status == 408 || status == 429 || status >= 500) {
            category = "PROVIDER_UNAVAILABLE";
        } else {
            category = "HTTP_ERROR";
        }

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
