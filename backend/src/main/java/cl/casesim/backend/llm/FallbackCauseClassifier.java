package cl.casesim.backend.llm;

import java.util.Locale;
import java.util.function.Function;

public final class FallbackCauseClassifier {

    private FallbackCauseClassifier() {
    }

    public static String classifyFallbackCause(RuntimeException ex) {
        return classifyFallbackCause(ex, Function.identity());
    }

    public static String classifyFallbackCause(RuntimeException ex, Function<String, String> sanitizer) {
        if (ex == null) {
            return "UNKNOWN";
        }
        if (ex instanceof LlmClientException llmEx && llmEx.providerError() != null && llmEx.providerError().category() != null) {
            return llmEx.providerError().category().name();
        }
        Function<String, String> safeSanitizer = sanitizer == null ? Function.identity() : sanitizer;
        String sanitizedMessage = safeSanitizer.apply(ex.getMessage());
        String message = sanitizedMessage == null ? "" : sanitizedMessage.toLowerCase(Locale.ROOT);
        if (message.contains("timeout") || message.contains("timed out")) {
            return "TIMEOUT";
        }
        if (message.contains("context") && (message.contains("length") || message.contains("token") || message.contains("max"))) {
            return "TOKEN_LIMIT_OR_CONTEXT_LENGTH";
        }
        if (message.contains("json") || message.contains("parse") || message.contains("parseable")) {
            return "PARSE_JSON";
        }
        if (message.contains("payload") || message.contains("messages") || message.contains("input")) {
            return "PROMPT_MALFORMED_OR_PAYLOAD_INVALID";
        }
        if (message.contains("status=429")
                || message.contains("quota")
                || message.contains("insufficient")
                || message.contains("rate limit")) {
            return "QUOTA_EXCEEDED";
        }
        if (message.contains("model_invalid") || (message.contains("model") && (message.contains("invalid") || message.contains("not found") || message.contains("does not exist")))) {
            return "MODEL_OR_PARAMETER_INCOMPATIBILITY";
        }
        if (message.contains("serializ") || message.contains("deserialize")) {
            return "SERIALIZATION";
        }
        if (message.contains("null")) {
            return "NULL_VALUES";
        }
        if (message.contains("response vac") || message.contains("no parseable") || message.contains("http error") || message.contains("provider")) {
            return "PROVIDER_RESPONSE_INVALID";
        }
        return "UNKNOWN";
    }

    public static boolean isLikelyQuotaMessage(String userMessage) {
        String normalized = TextNormalizationUtil.normalize(userMessage);
        return normalized.contains("quota")
                || normalized.contains("sin cuota")
                || normalized.contains("sin saldo")
                || normalized.contains("no responde")
                || normalized.contains("no contestas")
                || normalized.contains("silencio");
    }

    public static boolean isLikelyGreeting(String userMessage) {
        String normalized = TextNormalizationUtil.normalize(userMessage);
        if (!TextNormalizationUtil.hasText(normalized)) {
            return true;
        }
        return normalized.equals("hola")
                || normalized.equals("buenas")
                || normalized.equals("buenos dias")
                || normalized.equals("buenas tardes")
                || normalized.equals("buenas noches");
    }

    public static boolean isLikelyFollowUp(String userMessage) {
        String normalized = TextNormalizationUtil.normalize(userMessage);
        return TextNormalizationUtil.hasText(normalized) && !isLikelyGreeting(normalized);
    }
}
