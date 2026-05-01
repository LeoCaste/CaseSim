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
}
