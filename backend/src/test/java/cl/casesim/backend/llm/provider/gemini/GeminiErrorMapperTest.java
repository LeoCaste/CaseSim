package cl.casesim.backend.llm.provider.gemini;

import cl.casesim.backend.llm.LlmErrorCategory;
import cl.casesim.backend.llm.LlmProviderError;
import cl.casesim.backend.llm.LlmProviderErrorMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeminiErrorMapperTest {

    @Test
    void status429ConQuotaSeMapeaQuotaExceeded() {
        GeminiErrorMapper mapper = new GeminiErrorMapper(new LlmProviderErrorMapper());

        LlmProviderError error = mapper.mapHttpError(429, "RESOURCE_EXHAUSTED: You exceeded your current quota");

        assertEquals(LlmErrorCategory.QUOTA_EXCEEDED, error.category());
    }

    @Test
    void status429SinQuotaSeMapeaRateLimit() {
        GeminiErrorMapper mapper = new GeminiErrorMapper(new LlmProviderErrorMapper());

        LlmProviderError error = mapper.mapHttpError(429, "Too many requests. Please retry later");

        assertEquals(LlmErrorCategory.RATE_LIMIT, error.category());
    }
}
