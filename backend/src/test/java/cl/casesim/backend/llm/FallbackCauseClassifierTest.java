package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackCauseClassifierTest {

    @Test
    void classifyFallbackCauseConservaCategoriasPorMensajeSanitizado() {
        assertEquals("TIMEOUT", classify("request timeout"));
        assertEquals("TOKEN_LIMIT_OR_CONTEXT_LENGTH", classify("context length max token"));
        assertEquals("PARSE_JSON", classify("json parse error"));
        assertEquals("PROMPT_MALFORMED_OR_PAYLOAD_INVALID", classify("invalid payload messages input"));
        assertEquals("QUOTA_EXCEEDED", classify("Error status=429 insufficient quota"));
        assertEquals("MODEL_OR_PARAMETER_INCOMPATIBILITY", classify("model_invalid"));
        assertEquals("SERIALIZATION", classify("serialization failed"));
        assertEquals("NULL_VALUES", classify("null value"));
        assertEquals("PROVIDER_RESPONSE_INVALID", classify("provider http error response vacia"));
        assertEquals("UNKNOWN", classify("otro error"));
        assertEquals("UNKNOWN", FallbackCauseClassifier.classifyFallbackCause(null));
    }

    @Test
    void classifyFallbackCausePriorizaCategoriaDeProviderError() {
        LlmProviderError providerError = new LlmProviderError(LlmErrorCategory.RATE_LIMIT, 429, "rate limit");
        LlmClientException ex = new LlmClientException("timeout", new RuntimeException("cause"), providerError);

        assertEquals("RATE_LIMIT", FallbackCauseClassifier.classifyFallbackCause(ex, message -> "timeout"));
    }

    @Test
    void quotaYRateLimitSeClasificanIgual() {
        assertEquals("QUOTA_EXCEEDED", classify("quota exceeded"));
        assertEquals("QUOTA_EXCEEDED", classify("rate limit reached"));
        assertEquals("QUOTA_EXCEEDED", classify("insufficient_quota"));
    }

    @Test
    void greetingYFollowUpSeDetectanIgual() {
        assertTrue(FallbackCauseClassifier.isLikelyGreeting(null));
        assertTrue(FallbackCauseClassifier.isLikelyGreeting("  "));
        assertTrue(FallbackCauseClassifier.isLikelyGreeting("Buenos días"));
        assertFalse(FallbackCauseClassifier.isLikelyGreeting("Hola, tengo dolor"));

        assertFalse(FallbackCauseClassifier.isLikelyFollowUp("hola"));
        assertTrue(FallbackCauseClassifier.isLikelyFollowUp("¿Desde cuándo?"));
    }

    @Test
    void quotaMessageSeDetectaIgual() {
        assertTrue(FallbackCauseClassifier.isLikelyQuotaMessage("sin saldo"));
        assertTrue(FallbackCauseClassifier.isLikelyQuotaMessage("no respondes, silencio"));
        assertFalse(FallbackCauseClassifier.isLikelyQuotaMessage("tengo dolor"));
    }

    private String classify(String message) {
        return FallbackCauseClassifier.classifyFallbackCause(new RuntimeException(message), value -> value);
    }
}
