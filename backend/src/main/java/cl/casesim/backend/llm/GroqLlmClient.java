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
    private static final String GROQ_CHAT_COMPLETIONS_PATH = "/openai/v1/chat/completions";
    private static final String GROQ_DEFAULT_URL = "https://api.groq.com" + GROQ_CHAT_COMPLETIONS_PATH;

    private final RestClient restClient;
    private final LlmProperties llmProperties;

    public GroqLlmClient(LlmProperties llmProperties) {
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
        if (!LlmProviderSupport.GROQ.equals(provider)) {
            throw new OpenAiLlmClient.LlmClientException("Proveedor no implementado: " + provider);
        }
        if (!StringUtils.hasText(llmProperties.getApiKey())) {
            throw new OpenAiLlmClient.LlmClientException("API key no configurada.");
        }

        int attempts = Math.max(1, llmProperties.getMaxRetries() + 1);
        String resolvedUrl = resolveGroqUrl();
        String requestPath = resolveRequestPath(resolvedUrl);
        String requestHost = resolveRequestHost(resolvedUrl);
        String authHeader = "Bearer " + llmProperties.getApiKey().trim();
        boolean hasAuthHeader = StringUtils.hasText(authHeader);

        log.info("LLM client request provider={} model={} host={} path={} messagesCount={} temperature={} maxTokens={} authHeaderPresent={} authPrefix={}",
                provider,
                llmProperties.getModel(),
                requestHost,
                requestPath,
                messages == null ? 0 : messages.size(),
                temperature == null ? llmProperties.getTemperature() : temperature,
                maxTokens == null ? llmProperties.getMaxTokens() : maxTokens,
                hasAuthHeader,
                maskedAuthPrefix(authHeader));

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
                    throw new OpenAiLlmClient.LlmClientException("Proveedor LLM devolvió respuesta vacía o no parseable.");
                }
                return content;
            } catch (RestClientException ex) {
                if (ex instanceof ResourceAccessException) {
                    if (attempt == attempts) {
                        throw new OpenAiLlmClient.LlmClientException("Timeout al invocar proveedor LLM", ex);
                    }
                    continue;
                }
                if (ex instanceof RestClientResponseException responseException) {
                    int status = responseException.getStatusCode().value();
                    String category = mapHttpErrorCategory(status, responseException.getResponseBodyAsString());
                    log.warn("LLM client http error provider={} model={} host={} path={} status={} category={} errorBody={} authHeaderPresent={} authPrefix={}",
                            provider,
                            llmProperties.getModel(),
                            requestHost,
                            requestPath,
                            status,
                            category,
                            sanitizeProviderBody(responseException.getResponseBodyAsString()),
                            hasAuthHeader,
                            maskedAuthPrefix(authHeader));
                    String message = buildHttpErrorMessage(responseException, requestPath);
                    if (attempt == attempts) {
                        throw new OpenAiLlmClient.LlmClientException(message, ex);
                    }
                    continue;
                }
                if (attempt == attempts) {
                    throw new OpenAiLlmClient.LlmClientException("Error invocando proveedor LLM", ex);
                }
            }
        }

        throw new OpenAiLlmClient.LlmClientException("No fue posible obtener respuesta del proveedor LLM");
    }

    Map<String, Object> buildGroqPayload(List<ChatPromptMessage> messages, Double temperature, Integer maxTokens) {
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
        String category = mapHttpErrorCategory(status, body);

        String sanitizedBody = sanitizeProviderBody(body);
        String suffix = StringUtils.hasText(sanitizedBody) ? " detail=" + sanitizedBody : "";
        return "Error HTTP proveedor LLM status=" + status + " category=" + category + " path=" + requestPath + suffix;
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

    String resolveGroqUrl() {
        String configured = llmProperties.getBaseUrl();
        String normalizedBase = sanitizeGroqBaseUrl(configured);
        try {
            URI uri = new URI(normalizedBase);
            String currentPath = uri.getPath() == null ? "" : uri.getPath().trim();
            String normalizedPath = normalizeGroqPath(currentPath);
            URI normalizedUri = new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    normalizedPath,
                    uri.getQuery(),
                    uri.getFragment()
            );
            return normalizedUri.toString();
        } catch (URISyntaxException ignored) {
            return GROQ_DEFAULT_URL;
        }
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

    private String normalizeGroqPath(String path) {
        if (!StringUtils.hasText(path) || "/".equals(path)) {
            return GROQ_CHAT_COMPLETIONS_PATH;
        }

        String normalized = path.replaceAll("/+", "/");
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.endsWith("/openai/v1/chat/completions") || normalized.endsWith("/v1/chat/completions")) {
            if (normalized.endsWith("/v1/chat/completions") && !normalized.contains("/openai/")) {
                return normalized.substring(0, normalized.length() - "/v1/chat/completions".length()) + GROQ_CHAT_COMPLETIONS_PATH;
            }
            return normalized;
        }

        if (normalized.endsWith("/openai")) {
            return normalized + "/v1/chat/completions";
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + GROQ_CHAT_COMPLETIONS_PATH;
    }

    private String sanitizeGroqBaseUrl(String configuredBaseUrl) {
        if (!StringUtils.hasText(configuredBaseUrl)) {
            return GROQ_DEFAULT_URL;
        }

        String trimmed = configuredBaseUrl.trim();
        try {
            URI uri = new URI(trimmed);
            String host = uri.getHost();
            if (!isAllowedGroqHost(host)) {
                log.info("Groq baseUrl host no permitido para provider=groq; se usará fallback seguro. configuredHost={} fallbackHost=api.groq.com",
                        host == null ? "" : host);
                return GROQ_DEFAULT_URL;
            }
            return trimmed;
        } catch (URISyntaxException ignored) {
            log.info("Groq baseUrl inválida para provider=groq; se usará fallback seguro. fallbackHost=api.groq.com");
            return GROQ_DEFAULT_URL;
        }
    }

    private boolean isAllowedGroqHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if ("api.openai.com".equals(normalized) || normalized.endsWith(".openai.com") || "openai.com".equals(normalized)) {
            return false;
        }
        return "api.groq.com".equals(normalized) || normalized.endsWith(".groq.com") || "localhost".equals(normalized) || "127.0.0.1".equals(normalized);
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

    private String maskedAuthPrefix(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return "none";
        }
        String value = authorizationHeader.trim();
        return value.length() <= 14 ? "present" : value.substring(0, 14) + "***";
    }

}
