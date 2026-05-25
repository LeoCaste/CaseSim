package cl.casesim.backend.llm;

import org.springframework.util.StringUtils;

import java.util.Locale;

public class LlmProviderErrorMapper {

    public LlmProviderError map(int status, String body) {
        String bodyLower = body == null ? "" : body.toLowerCase(Locale.ROOT);
        LlmErrorCategory category;
        if (status == 401 || status == 403) {
            category = LlmErrorCategory.AUTH_ERROR;
        } else if (status == 429 && isQuotaExceeded(bodyLower)) {
            category = LlmErrorCategory.QUOTA_EXCEEDED;
        } else if (status == 429) {
            category = LlmErrorCategory.RATE_LIMIT;
        } else if (status == 404 && bodyLower.contains("model")) {
            category = LlmErrorCategory.MODEL_NOT_FOUND;
        } else if (status >= 500) {
            category = LlmErrorCategory.PROVIDER_UNAVAILABLE;
        } else if (status == 400) {
            category = LlmErrorCategory.INVALID_REQUEST;
        } else {
            category = LlmErrorCategory.UNKNOWN;
        }
        String sanitizedBody = StringUtils.hasText(body) ? body.replaceAll("\\s+", " ").trim() : "";
        if (sanitizedBody.length() > 240) {
            sanitizedBody = sanitizedBody.substring(0, 240) + "...";
        }
        return new LlmProviderError(category, status, sanitizedBody);
    }

    private boolean isQuotaExceeded(String bodyLower) {
        return bodyLower.contains("insufficient_quota")
                || bodyLower.contains("quota")
                || bodyLower.contains("credit")
                || bodyLower.contains("billing")
                || bodyLower.contains("payment required");
    }
}
