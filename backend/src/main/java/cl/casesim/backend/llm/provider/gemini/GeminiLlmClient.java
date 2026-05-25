package cl.casesim.backend.llm.provider.gemini;

import cl.casesim.backend.llm.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

public class GeminiLlmClient implements LlmClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GeminiLlmClient.class);

    private final RestClient restClient;
    private final LlmProperties llmProperties;
    private final LlmProviderUrlResolver urlResolver;
    private final GeminiRequestMapper requestMapper;
    private final GeminiResponseMapper responseMapper;
    private final GeminiErrorMapper errorMapper;

    public GeminiLlmClient(
            LlmProperties llmProperties,
            LlmProviderUrlResolver urlResolver,
            GeminiRequestMapper requestMapper,
            GeminiResponseMapper responseMapper,
            GeminiErrorMapper errorMapper
    ) {
        this.llmProperties = llmProperties;
        this.urlResolver = urlResolver;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
        this.errorMapper = errorMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(llmProperties.getTimeoutMs());
        requestFactory.setReadTimeout(llmProperties.getTimeoutMs());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String providerType() {
        return LlmProviderSupport.GEMINI;
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        String provider = LlmProviderSupport.normalize(llmProperties.getProvider());
        if (!LlmProviderSupport.GEMINI.equals(provider)) {
            throw new LlmClientException("Proveedor no implementado: " + provider);
        }
        if (!StringUtils.hasText(llmProperties.getApiKey())) {
            throw new LlmClientException("API key no configurada.");
        }

        String model = StringUtils.hasText(llmProperties.getModel()) ? llmProperties.getModel().trim() : GeminiModels.DEFAULT;
        String requestUrl = urlResolver.resolveGeminiGenerateContentUrl(llmProperties.getBaseUrl(), model);
        int attempts = Math.max(1, llmProperties.getMaxRetries() + 1);

        log.info("LLM client request provider={} model={} endpoint={} messagesCount={} temperature={} maxTokens={}",
                provider,
                model,
                requestUrl,
                request.messages() == null ? 0 : request.messages().size(),
                request.temperature() == null ? llmProperties.getTemperature() : request.temperature(),
                request.maxTokens() == null ? llmProperties.getMaxTokens() : request.maxTokens());

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restClient.post()
                        .uri(requestUrl)
                        .header("x-goog-api-key", llmProperties.getApiKey().trim())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestMapper.toGenerateContentPayload(request, llmProperties))
                        .retrieve()
                        .body(Map.class);

                String content = responseMapper.extractContent(response);
                return new LlmResponse(
                        content,
                        null,
                        new LlmProviderResult(provider, model, requestUrl, null)
                );
            } catch (RestClientException ex) {
                if (ex instanceof ResourceAccessException) {
                    if (attempt == attempts) {
                        throw new LlmClientException("Timeout al invocar proveedor LLM", ex, errorMapper.timeout());
                    }
                    continue;
                }
                if (ex instanceof RestClientResponseException responseException) {
                    LlmProviderError providerError = errorMapper.mapHttpError(
                            responseException.getStatusCode().value(),
                            sanitizeBody(responseException.getResponseBodyAsString())
                    );
                    if (attempt == attempts) {
                        throw new LlmClientException(
                                "Error HTTP proveedor LLM status=" + responseException.getStatusCode().value() + " path=:generateContent",
                                ex,
                                providerError
                        );
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

    private String sanitizeBody(String body) {
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
