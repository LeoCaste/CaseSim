package cl.casesim.backend.llm.provider.gemini;

import cl.casesim.backend.llm.LlmErrorCategory;
import cl.casesim.backend.llm.LlmProviderError;
import cl.casesim.backend.llm.LlmProviderErrorMapper;

import java.util.Locale;

public class GeminiErrorMapper {

    private final LlmProviderErrorMapper fallbackMapper;

    public GeminiErrorMapper(LlmProviderErrorMapper fallbackMapper) {
        this.fallbackMapper = fallbackMapper;
    }

    public LlmProviderError mapHttpError(int status, String body) {
        if (status == 400) {
            return new LlmProviderError(LlmErrorCategory.INVALID_REQUEST, status, sanitize(body));
        }
        if (status == 401 || status == 403) {
            return new LlmProviderError(LlmErrorCategory.AUTH_ERROR, status, sanitize(body));
        }
        if (status == 429) {
            String sanitizedBody = sanitize(body);
            if (isQuotaLike429(sanitizedBody)) {
                return new LlmProviderError(LlmErrorCategory.QUOTA_EXCEEDED, status, sanitizedBody);
            }
            return new LlmProviderError(LlmErrorCategory.RATE_LIMIT, status, sanitizedBody);
        }
        if (status >= 500) {
            return new LlmProviderError(LlmErrorCategory.PROVIDER_UNAVAILABLE, status, sanitize(body));
        }
        return fallbackMapper.map(status, body);
    }

    public LlmProviderError timeout() {
        return new LlmProviderError(LlmErrorCategory.TIMEOUT, null, "timeout");
    }

    public LlmProviderError emptyResponse() {
        return new LlmProviderError(LlmErrorCategory.INVALID_RESPONSE, null, "empty_response");
    }

    private String sanitize(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() > 240 ? normalized.substring(0, 240) + "..." : normalized;
    }

    private boolean isQuotaLike429(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String normalized = body.toLowerCase(Locale.ROOT);
        return normalized.contains("insufficient_quota")
                || normalized.contains("quota")
                || normalized.contains("resource_exhausted")
                || normalized.contains("billing")
                || (normalized.contains("exceeded") && normalized.contains("limit"));
    }
}
