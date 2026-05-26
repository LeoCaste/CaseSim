package cl.casesim.backend.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmProviderErrorMapperTest {

    @Test
    void status401MapeaAuthError() {
        LlmProviderErrorMapper mapper = new LlmProviderErrorMapper();
        LlmProviderError error = mapper.map(401, "invalid key");
        assertEquals(LlmErrorCategory.AUTH_ERROR, error.category());
    }

    @Test
    void status429InsufficientQuotaMapeaQuotaExceeded() {
        LlmProviderErrorMapper mapper = new LlmProviderErrorMapper();
        LlmProviderError error = mapper.map(429, "insufficient_quota");
        assertEquals(LlmErrorCategory.QUOTA_EXCEEDED, error.category());
    }

    @Test
    void status429ConMensajeDeCreditosMapeaQuotaExceeded() {
        LlmProviderErrorMapper mapper = new LlmProviderErrorMapper();
        LlmProviderError error = mapper.map(429, "No credits left for this request");
        assertEquals(LlmErrorCategory.QUOTA_EXCEEDED, error.category());
    }

    @Test
    void status404ConModeloMapeaModelNotFound() {
        LlmProviderErrorMapper mapper = new LlmProviderErrorMapper();
        LlmProviderError error = mapper.map(404, "model not found");
        assertEquals(LlmErrorCategory.MODEL_NOT_FOUND, error.category());
    }

    @Test
    void status400ConModeloInvalidoMapeaModelNotFound() {
        LlmProviderErrorMapper mapper = new LlmProviderErrorMapper();
        LlmProviderError error = mapper.map(400, "Invalid model: anthropic/claude-3.5-sonnet does not exist");
        assertEquals(LlmErrorCategory.MODEL_NOT_FOUND, error.category());
    }

    @Test
    void status503MapeaProviderUnavailable() {
        LlmProviderErrorMapper mapper = new LlmProviderErrorMapper();
        LlmProviderError error = mapper.map(503, "temporary upstream failure");
        assertEquals(LlmErrorCategory.PROVIDER_UNAVAILABLE, error.category());
    }

    @Test
    void status404ConNoRouteModelMapeaModelNotFound() {
        LlmProviderErrorMapper mapper = new LlmProviderErrorMapper();
        LlmProviderError error = mapper.map(404, "No route found for model anthropic/claude-3.5-sonnet");
        assertEquals(LlmErrorCategory.MODEL_NOT_FOUND, error.category());
    }

    @Test
    void status404ConAccessDeniedMapeaAuthError() {
        LlmProviderErrorMapper mapper = new LlmProviderErrorMapper();
        LlmProviderError error = mapper.map(404, "Access denied for this model");
        assertEquals(LlmErrorCategory.AUTH_ERROR, error.category());
    }

    @Test
    void status404GenericoMapeaInvalidRequest() {
        LlmProviderErrorMapper mapper = new LlmProviderErrorMapper();
        LlmProviderError error = mapper.map(404, "Not Found");
        assertEquals(LlmErrorCategory.INVALID_REQUEST, error.category());
    }
}
