package cl.casesim.backend.llm;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroqLlmClient implements LlmClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GroqLlmClient.class);
    private final RestClient restClient;
    private final LlmProperties llmProperties;
    private final LlmProviderUrlResolver urlResolver;
    private final LlmProviderErrorMapper errorMapper;

    public GroqLlmClient(LlmProperties llmProperties, LlmProviderUrlResolver urlResolver, LlmProviderErrorMapper errorMapper) {
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
        return LlmProviderSupport.GROQ;
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        List<LlmMessage> messages = request.messages();
        Double temperature = request.temperature();
        Integer maxTokens = request.maxTokens();
        String provider = LlmProviderSupport.normalize(llmProperties.getProvider());
        if (!LlmProviderSupport.GROQ.equals(provider)) {
            throw new LlmClientException("Proveedor no implementado: " + provider);
        }
        if (!StringUtils.hasText(llmProperties.getApiKey())) {
            throw new LlmClientException("API key no configurada.");
        }

        int attempts = Math.max(1, llmProperties.getMaxRetries() + 1);
        String normalizedBaseUrl = urlResolver.resolveBaseUrl(LlmProviderSupport.GROQ, llmProperties.getBaseUrl());
        String finalPath = urlResolver.resolveChatCompletionsPath(LlmProviderSupport.GROQ);
        String resolvedUrl = urlResolver.resolve(LlmProviderSupport.GROQ, llmProperties.getBaseUrl());
        String requestHost = resolveRequestHost(normalizedBaseUrl);
        boolean customBaseUrl = StringUtils.hasText(llmProperties.getBaseUrl());
        String authHeader = "Bearer " + llmProperties.getApiKey().trim();
        boolean hasAuthHeader = StringUtils.hasText(authHeader);

        log.info("LLM client request provider={} model={} host={} normalizedBaseUrl={} finalPath={} customBaseUrl={} messagesCount={} temperature={} maxTokens={} authHeaderPresent={}",
                provider,
                llmProperties.getModel(),
                requestHost,
                normalizedBaseUrl,
                finalPath,
                customBaseUrl,
                messages == null ? 0 : messages.size(),
                temperature == null ? llmProperties.getTemperature() : temperature,
                maxTokens == null ? llmProperties.getMaxTokens() : maxTokens,
                hasAuthHeader);

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restClient.post()
                        .uri(resolvedUrl)
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(buildGroqPayload(messages, temperature, maxTokens))
                        .retrieve()
                        .body(Map.class);

                String content = extractGroqContent(response);
                if (!StringUtils.hasText(content)) {
                    throw new LlmClientException("Proveedor LLM devolvió respuesta vacía o no parseable.");
                }
                return new LlmResponse(content, extractUsage(response), new LlmProviderResult(provider, llmProperties.getModel(), resolvedUrl, null));
            } catch (RestClientException ex) {
                if (ex instanceof ResourceAccessException) {
                    if (attempt == attempts) {
                        throw new LlmClientException("Timeout al invocar proveedor LLM", ex);
                    }
                    continue;
                }
                if (ex instanceof RestClientResponseException responseException) {
                    int status = responseException.getStatusCode().value();
                    LlmProviderError providerError = errorMapper.map(status, responseException.getResponseBodyAsString());
                    String category = providerError.category().name();
                    log.warn("LLM client http error provider={} model={} host={} finalPath={} status={} category={} errorBody={} authHeaderPresent={}",
                            provider,
                            llmProperties.getModel(),
                            requestHost,
                            finalPath,
                            status,
                            category,
                            sanitizeProviderBody(responseException.getResponseBodyAsString()),
                            hasAuthHeader);
                    String message = buildHttpErrorMessage(responseException, finalPath);
                    if (attempt == attempts) {
                        throw new LlmClientException(message, ex, providerError);
                    }
                    continue;
                }
                if (attempt == attempts) {
                    throw new LlmClientException("Error invocando proveedor LLM", ex);
                }
            }
        }

        throw new LlmClientException("No fue posible obtener respuesta del proveedor LLM");
    }

    Map<String, Object> buildGroqPayload(List<LlmMessage> messages, Double temperature, Integer maxTokens) {
        double resolvedTemperature = temperature == null ? llmProperties.getTemperature() : temperature;
        int resolvedMaxTokens = maxTokens == null ? llmProperties.getMaxTokens() : maxTokens;
        return Map.of(
                "model", llmProperties.getModel(),
                "messages", messages,
                "temperature", resolvedTemperature,
                "max_tokens", resolvedMaxTokens
        );
    }

    @SuppressWarnings("unchecked")
    String extractGroqContent(Map<String, Object> response) {
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
        if (!(contentObj instanceof String content) || !StringUtils.hasText(content)) {
            return "";
        }
        return content.trim();
    }

    String mapHttpErrorMessage(int status, String body, String requestPath) {
        String category = errorMapper.map(status, body).category().name();

        String sanitizedBody = sanitizeProviderBody(body);
        String suffix = StringUtils.hasText(sanitizedBody) ? " detail=" + sanitizedBody : "";
        return "Error HTTP proveedor LLM status=" + status + " category=" + category + " path=" + requestPath + suffix;
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
        Integer prompt = toInteger(usage.get("prompt_tokens"));
        Integer completion = toInteger(usage.get("completion_tokens"));
        Integer total = toInteger(usage.get("total_tokens"));
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

    private String buildHttpErrorMessage(RestClientResponseException responseException, String requestPath) {
        return mapHttpErrorMessage(responseException.getStatusCode().value(), responseException.getResponseBodyAsString(), requestPath);
    }

    private String mapHttpErrorCategory(int status, String body) {
        String bodyLower = body == null ? "" : body.toLowerCase();
        if (status == 400 && bodyLower.contains("model") && (bodyLower.contains("invalid") || bodyLower.contains("does not exist") || bodyLower.contains("not found"))) {
            return "MODEL_INVALID";
        }
        if (status == 400 && (bodyLower.contains("messages") || bodyLower.contains("input"))) {
            return "PAYLOAD_INVALID";
        }
        if (status == 401 || status == 403) {
            return "AUTH_ERROR";
        }
        if (status == 408 || status == 429 || status >= 500) {
            return "PROVIDER_UNAVAILABLE";
        }
        return "HTTP_ERROR";
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

    String resolveRequestPath(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            URI uri = new URI(url.trim());
            String path = uri.getPath() == null ? "" : uri.getPath();
            return StringUtils.hasText(path) ? path : "";
        } catch (URISyntaxException ignored) {
            return "";
        }
    }

    private String resolveRequestHost(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        try {
            URI uri = new URI(url.trim());
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException ignored) {
            return "";
        }
    }

}
