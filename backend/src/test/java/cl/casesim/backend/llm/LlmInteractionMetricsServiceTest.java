package cl.casesim.backend.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LlmInteractionMetricsServiceTest {

    private final LlmUsageService llmUsageService = mock(LlmUsageService.class);
    private LlmInteractionMetricsService service;

    @BeforeEach
    void setUp() {
        service = new LlmInteractionMetricsService(llmUsageService);
    }

    // ──────────────────────────────────────────────
    // safeRegisterUsage
    // ──────────────────────────────────────────────

    @Test
    void registraExitoConProviderModelTokens() {
        UUID sessionId = UUID.randomUUID();

        service.safeRegisterUsage(sessionId, "openai", "gpt-4", 150, 50, 1200, false, null);

        verify(llmUsageService).registerCall(sessionId, "openai", "gpt-4", 150, 50, 1200, false, null);
    }

    @Test
    void registraFallbackConFallbackTrue() {
        UUID sessionId = UUID.randomUUID();

        service.safeRegisterUsage(sessionId, "openai", "gpt-4", 100, 20, 500, true, "PROVIDER_CALL_ERROR|...");

        verify(llmUsageService).registerCall(sessionId, "openai", "gpt-4", 100, 20, 500, true, "PROVIDER_CALL_ERROR|...");
    }

    @Test
    void registraErrorLlmDeshabilitado() {
        UUID sessionId = UUID.randomUUID();

        service.safeRegisterUsage(sessionId, "openai", "gpt-4", 0, 0, 0, true, "LLM_DISABLED_OR_MISSING_API_KEY");

        verify(llmUsageService).registerCall(sessionId, "openai", "gpt-4", 0, 0, 0, true, "LLM_DISABLED_OR_MISSING_API_KEY");
    }

    @Test
    void registraErrorContexto() {
        UUID sessionId = UUID.randomUUID();

        service.safeRegisterUsage(sessionId, "openai", "gpt-4", 50, 10, 200, true, "CONTEXT_LOAD_ERROR|ContextResolutionException|...");

        verify(llmUsageService).registerCall(sessionId, "openai", "gpt-4", 50, 10, 200, true, "CONTEXT_LOAD_ERROR|ContextResolutionException|...");
    }

    @Test
    void registraErrorProvider() {
        UUID sessionId = UUID.randomUUID();

        service.safeRegisterUsage(sessionId, "openai", "gpt-4", 80, 15, 3000, true, "PROVIDER_CALL_ERROR|LLMClientException|...");

        verify(llmUsageService).registerCall(sessionId, "openai", "gpt-4", 80, 15, 3000, true, "PROVIDER_CALL_ERROR|LLMClientException|...");
    }

    @Test
    void siLlmUsageServiceLanzaExcepcionNoPropaga() {
        UUID sessionId = UUID.randomUUID();
        doThrow(new RuntimeException("DB connection failed"))
                .when(llmUsageService).registerCall(any(), any(), any(), anyInt(), anyInt(), any(), anyBoolean(), any());

        assertDoesNotThrow(() ->
                service.safeRegisterUsage(sessionId, "openai", "gpt-4", 10, 5, 100, false, null)
        );
    }

    // ──────────────────────────────────────────────
    // resolveMetricProvider
    // ──────────────────────────────────────────────

    @Test
    void resolveMetricProviderConResponseValida() {
        LlmResponse response = new LlmResponse("ok", null,
                new LlmProviderResult("gemini", "gemini-2.0", "url", null));

        String result = service.resolveMetricProvider(response, "fallback");

        assertEquals("gemini", result);
    }

    @Test
    void resolveMetricProviderConResponseNula() {
        String result = service.resolveMetricProvider(null, "fallback");

        assertEquals("fallback", result);
    }

    @Test
    void resolveMetricProviderConResponseSinProviderResult() {
        LlmResponse response = new LlmResponse("ok", null, null);

        String result = service.resolveMetricProvider(response, "fallback");

        assertEquals("fallback", result);
    }

    @Test
    void resolveMetricProviderConProviderVacio() {
        LlmResponse response = new LlmResponse("ok", null,
                new LlmProviderResult("", "gemini-2.0", "url", null));

        String result = service.resolveMetricProvider(response, "fallback");

        assertEquals("fallback", result);
    }

    // ──────────────────────────────────────────────
    // resolveMetricModel
    // ──────────────────────────────────────────────

    @Test
    void resolveMetricModelConResponseValida() {
        LlmResponse response = new LlmResponse("ok", null,
                new LlmProviderResult("gemini", "gemini-2.5-flash-lite", "url", null));

        String result = service.resolveMetricModel(response, "fallback");

        assertEquals("gemini-2.5-flash-lite", result);
    }

    @Test
    void resolveMetricModelConResponseNula() {
        String result = service.resolveMetricModel(null, "fallback");

        assertEquals("fallback", result);
    }

    @Test
    void resolveMetricModelConResponseSinProviderResult() {
        LlmResponse response = new LlmResponse("ok", null, null);

        String result = service.resolveMetricModel(response, "fallback");

        assertEquals("fallback", result);
    }

    @Test
    void resolveMetricModelConModelVacio() {
        LlmResponse response = new LlmResponse("ok", null,
                new LlmProviderResult("gemini", "", "url", null));

        String result = service.resolveMetricModel(response, "fallback");

        assertEquals("fallback", result);
    }

    // ──────────────────────────────────────────────
    // resolvePromptTokens
    // ──────────────────────────────────────────────

    @Test
    void resolvePromptTokensConUsage() {
        LlmResponse response = new LlmResponse("ok", new LlmTokenUsage(101, 33, 134, false), null);

        Integer result = service.resolvePromptTokens(response);

        assertEquals(101, result);
    }

    @Test
    void resolvePromptTokensSinUsage() {
        LlmResponse response = new LlmResponse("ok", null, null);

        Integer result = service.resolvePromptTokens(response);

        assertNull(result);
    }

    @Test
    void resolvePromptTokensConResponseNula() {
        Integer result = service.resolvePromptTokens(null);

        assertNull(result);
    }

    // ──────────────────────────────────────────────
    // resolveCompletionTokens
    // ──────────────────────────────────────────────

    @Test
    void resolveCompletionTokensConUsage() {
        LlmResponse response = new LlmResponse("ok", new LlmTokenUsage(101, 33, 134, false), null);

        Integer result = service.resolveCompletionTokens(response);

        assertEquals(33, result);
    }

    @Test
    void resolveCompletionTokensSinUsage() {
        LlmResponse response = new LlmResponse("ok", null, null);

        Integer result = service.resolveCompletionTokens(response);

        assertNull(result);
    }

    @Test
    void resolveCompletionTokensConResponseNula() {
        Integer result = service.resolveCompletionTokens(null);

        assertNull(result);
    }

    // ──────────────────────────────────────────────
    // countSymptomFacts
    // ──────────────────────────────────────────────

    @Test
    void countSymptomFactsConFactsConSintomas() {
        List<String> facts = List.of(
                "Dolor abdominal intenso",
                "Tengo fiebre desde ayer",
                "Tuve cirugía hace 2 años",
                "Tos seca persistente",
                "Presenta disnea al esfuerzo"
        );

        int count = service.countSymptomFacts(facts);

        assertEquals(4, count);
    }

    @Test
    void countSymptomFactsConFactsVacios() {
        int count = service.countSymptomFacts(List.of());

        assertEquals(0, count);
    }

    @Test
    void countSymptomFactsConNull() {
        int count = service.countSymptomFacts(null);

        assertEquals(0, count);
    }

    @Test
    void countSymptomFactsSoloFactsSinSintomas() {
        List<String> facts = List.of(
                "Tuve cirugía hace 2 años",
                "Soy alérgica a la penicilina"
        );

        int count = service.countSymptomFacts(facts);

        assertEquals(0, count);
    }

    // ──────────────────────────────────────────────
    // estimateTokens
    // ──────────────────────────────────────────────

    @Test
    void estimateTokensDelegaEnLlmUsageService() {
        when(llmUsageService.estimateTokens("Hola mundo")).thenReturn(3);

        int result = service.estimateTokens("Hola mundo");

        assertEquals(3, result);
        verify(llmUsageService).estimateTokens("Hola mundo");
    }
}
