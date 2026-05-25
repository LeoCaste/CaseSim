package cl.casesim.backend.llm;

import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.llm.dto.LlmConfigResponse;
import cl.casesim.backend.llm.dto.TestConnectionResponse;
import cl.casesim.backend.llm.dto.UpdateLlmConfigRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmAdminServiceTest {

    private final LlmConfigRepository llmConfigRepository = mock(LlmConfigRepository.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final LlmUsageService llmUsageService = mock(LlmUsageService.class);
    private final LlmApiKeyCipher llmApiKeyCipher = new LlmApiKeyCipher("test-llm-key");
    private final LlmProperties llmProperties = new LlmProperties();

    private final LlmAdminService service = new LlmAdminService(
            llmConfigRepository,
            llmProperties,
            llmClient,
            llmUsageService,
            llmApiKeyCipher
    );

    @Test
    void getConfigShouldMaskApiKey() {
        LlmConfig config = new LlmConfig(
                UUID.randomUUID(),
                "openai",
                "gpt-4o-mini",
                "https://api.openai.com/v1/chat/completions",
                true,
                llmApiKeyCipher.encrypt("sk-1234567890"),
                LocalDateTime.now(),
                PromptBuilderService.defaultSystemPrompt(),
                "",
                "No tengo información asociada a eso.",
                RevealStrategy.PROGRESSIVE,
                6,
                0.4,
                350,
                true
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(config));

        LlmConfigResponse response = service.getConfig();

        assertTrue(response.apiKeyConfigured());
        assertFalse(response.maskedApiKey().contains("1234567890"));
        assertTrue(response.maskedApiKey().endsWith("7890"));
    }

    @Test
    void testConnectionShouldRegisterMetricsAndReturnSuccess() {
        llmProperties.setEnabled(true);
        llmProperties.setProvider("openai");
        llmProperties.setModel("gpt-4o-mini");
        llmProperties.setApiKey("sk-test");
        when(llmClient.generate(any())).thenReturn(new LlmResponse("pong", null, null));

        TestConnectionResponse response = service.testConnection();

        assertTrue(response.success());
        assertEquals("Conexión exitosa.", response.message());
        verify(llmUsageService).registerCall(any(), any(), any(), any(Integer.class), any(Integer.class), any(), any(Boolean.class), any());
    }

    @Test
    void testConnectionShouldSucceedEvenWhenMetricsRegistrationFails() {
        llmProperties.setEnabled(true);
        llmProperties.setProvider("openai");
        llmProperties.setModel("gpt-4o-mini");
        llmProperties.setApiKey("sk-test");
        when(llmClient.generate(any())).thenReturn(new LlmResponse("pong", null, null));
        doThrow(new RuntimeException("insert failed")).when(llmUsageService)
                .registerCall(any(), any(), any(), any(Integer.class), any(Integer.class), any(), any(Boolean.class), any());

        TestConnectionResponse response = service.testConnection();

        assertTrue(response.success());
        assertEquals("Conexión exitosa.", response.message());
        verify(llmUsageService).registerCall(any(), any(), any(), any(Integer.class), any(Integer.class), any(), any(Boolean.class), any());
    }

    @Test
    void updateConfigShouldPersistAndApplyToRuntime() {
        UpdateLlmConfigRequest request = new UpdateLlmConfigRequest(
                "openai",
                "gpt-4.1-mini",
                "https://api.openai.com/v1/chat/completions",
                true,
                "sk-new",
                "",
                "responde corto",
                "No tengo información asociada a eso.",
                RevealStrategy.DIRECT,
                8,
                0.7,
                400,
                true,
                null
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());
        when(llmConfigRepository.save(any(LlmConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LlmConfigResponse response = service.updateConfig(request);

        assertEquals("openai", llmProperties.getProvider());
        assertEquals("gpt-4.1-mini", llmProperties.getModel());
        assertEquals("https://api.openai.com/v1/chat/completions", llmProperties.getBaseUrl());
        assertTrue(llmProperties.isEnabled());
        assertTrue(response.apiKeyConfigured());
    }

    @Test
    void updateConfigShouldAcceptModelWithoutClosedCatalogValidation() {
        UpdateLlmConfigRequest request = new UpdateLlmConfigRequest(
                "openai",
                "my-custom-model-v2026",
                "https://api.openai.com/v1/chat/completions",
                true,
                "sk-new",
                "",
                "responde corto",
                "No tengo información asociada a eso.",
                RevealStrategy.DIRECT,
                8,
                0.7,
                400,
                true,
                null
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());
        when(llmConfigRepository.save(any(LlmConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LlmConfigResponse response = service.updateConfig(request);

        assertEquals("my-custom-model-v2026", response.model());
        assertEquals("my-custom-model-v2026", llmProperties.getModel());
    }

    @Test
    void updateConfigShouldRejectEmptyModel() {
        UpdateLlmConfigRequest request = new UpdateLlmConfigRequest(
                "openai",
                "   ",
                "https://api.openai.com/v1/chat/completions",
                true,
                "sk-new",
                "",
                "responde corto",
                "No tengo información asociada a eso.",
                RevealStrategy.DIRECT,
                8,
                0.7,
                400,
                true,
                null
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> service.updateConfig(request));
    }

    @Test
    void updateConfigShouldRejectAnthropicAsNotImplementedForRealOperation() {
        UpdateLlmConfigRequest request = new UpdateLlmConfigRequest(
                "anthropic",
                "claude-3-5-sonnet",
                null,
                true,
                "sk-new",
                "",
                "responde corto",
                "No tengo información asociada a eso.",
                RevealStrategy.DIRECT,
                8,
                0.7,
                400,
                true,
                null
        );

        assertThrows(BadRequestException.class, () -> service.updateConfig(request));
    }

    @Test
    void updateConfigShouldAcceptGeminiAsRealProvider() {
        UpdateLlmConfigRequest request = new UpdateLlmConfigRequest(
                "gemini",
                "gemini-2.5-flash-lite",
                null,
                true,
                "gsk-test",
                "",
                "responde corto",
                "No tengo información asociada a eso.",
                RevealStrategy.DIRECT,
                8,
                0.7,
                400,
                true,
                null
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());
        when(llmConfigRepository.save(any(LlmConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LlmConfigResponse response = service.updateConfig(request);

        assertEquals("gemini", response.provider());
        assertEquals("gemini-2.5-flash-lite", response.model());
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models", llmProperties.getBaseUrl());
    }

    @Test
    void updateConfigShouldRequireApiKeyWhenNoExistingConfig() {
        UpdateLlmConfigRequest request = new UpdateLlmConfigRequest(
                "openai",
                "gpt-4.1-mini",
                null,
                true,
                "   ",
                "",
                "responde corto",
                "No tengo información asociada a eso.",
                RevealStrategy.DIRECT,
                8,
                0.7,
                400,
                true,
                null
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> service.updateConfig(request));
    }

    @Test
    void updateConfigShouldNotOverrideExistingApiKeyWhenRequestApiKeyIsBlank() {
        LocalDateTime now = LocalDateTime.now();
        LlmConfig existing = new LlmConfig(
                UUID.randomUUID(),
                "openai",
                "gpt-4o-mini",
                "https://api.openai.com/v1/chat/completions",
                true,
                llmApiKeyCipher.encrypt("sk-existing"),
                now,
                PromptBuilderService.defaultSystemPrompt(),
                "",
                "No tengo información asociada a eso.",
                RevealStrategy.PROGRESSIVE,
                6,
                0.4,
                350,
                true
        );
        UpdateLlmConfigRequest request = new UpdateLlmConfigRequest(
                "openai",
                "gpt-4.1-mini",
                null,
                true,
                "   ",
                "",
                "responde corto",
                "No tengo información asociada a eso.",
                RevealStrategy.DIRECT,
                8,
                0.7,
                400,
                true,
                null
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existing));
        when(llmConfigRepository.save(any(LlmConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LlmConfigResponse response = service.updateConfig(request);

        assertTrue(response.apiKeyConfigured());
        assertNotNull(response.maskedApiKey());
        assertEquals("sk-existing", llmProperties.getApiKey());
    }

    @Test
    void updateConfigShouldFailWhenMaxTokensOutOfRange() {
        UpdateLlmConfigRequest request = new UpdateLlmConfigRequest(
                "openai",
                "gpt-4.1-mini",
                "https://api.openai.com/v1/chat/completions",
                true,
                "sk-new",
                "",
                "responde corto",
                "No tengo información asociada a eso.",
                RevealStrategy.DIRECT,
                8,
                0.7,
                2048,
                true,
                null
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> service.updateConfig(request));
    }

    @Test
    void updateConfigShouldSupportNestedPatientBehaviorPayload() {
        UpdateLlmConfigRequest.PatientBehaviorPayload nestedBehavior = new UpdateLlmConfigRequest.PatientBehaviorPayload(
                "prompt base",
                "reglas",
                "No tengo información asociada a eso.",
                RevealStrategy.RESTRICTIVE,
                5,
                0.6,
                300,
                false
        );

        UpdateLlmConfigRequest request = new UpdateLlmConfigRequest(
                "openai",
                "gpt-4.1-mini",
                "https://api.openai.com/v1/chat/completions",
                true,
                "sk-new",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                nestedBehavior
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());
        when(llmConfigRepository.save(any(LlmConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LlmConfigResponse response = service.updateConfig(request);

        assertEquals("prompt base", response.systemPrompt());
        assertEquals("reglas", response.patientBehaviorRules());
        assertEquals(RevealStrategy.RESTRICTIVE, response.revealStrategy());
        assertEquals(5, response.maxHistoryMessages());
        assertEquals(0.6, response.temperature());
        assertEquals(300, response.maxTokens());
        assertFalse(response.enabledSafetyFilter());
    }

    @Test
    void deleteApiKeyShouldClearPersistedSecretAndKeepOtherConfig() {
        LocalDateTime now = LocalDateTime.now();
        LlmConfig existing = new LlmConfig(
                UUID.randomUUID(),
                "openai",
                "gpt-4o-mini",
                "https://api.openai.com/v1/chat/completions",
                true,
                llmApiKeyCipher.encrypt("sk-to-delete"),
                now,
                PromptBuilderService.defaultSystemPrompt(),
                "",
                "No tengo información asociada a eso.",
                RevealStrategy.PROGRESSIVE,
                6,
                0.4,
                350,
                true
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existing));
        when(llmConfigRepository.save(any(LlmConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LlmConfigResponse response = service.deleteApiKey();

        assertFalse(response.apiKeyConfigured());
        assertNull(response.maskedApiKey());
        assertEquals("openai", response.provider());
        assertEquals("gpt-4o-mini", response.model());
        assertFalse(response.enabled());
        assertEquals("", llmProperties.getApiKey());
        assertFalse(llmProperties.isEnabled());
    }

    @Test
    void testConnectionShouldFailClearlyWhenApiKeyWasRemoved() {
        llmProperties.setEnabled(true);
        llmProperties.setProvider("openai");
        llmProperties.setModel("gpt-4o-mini");
        llmProperties.setApiKey("");

        TestConnectionResponse response = service.testConnection();

        assertFalse(response.success());
        assertEquals("LLM deshabilitado o sin API key.", response.message());
        verify(llmUsageService).registerCall(any(), any(), any(), any(Integer.class), any(Integer.class), any(), any(Boolean.class), any());
    }

    @Test
    void testConnectionShouldDifferentiateAuthQuotaAndProviderErrors() {
        llmProperties.setEnabled(true);
        llmProperties.setProvider("openai");
        llmProperties.setModel("gpt-4o-mini");
        llmProperties.setApiKey("sk-test");

        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("401", null, new LlmProviderError(LlmErrorCategory.AUTH_ERROR, 401, "invalid")))
                .thenThrow(new LlmClientException("403", null, new LlmProviderError(LlmErrorCategory.AUTH_ERROR, 403, "forbidden")))
                .thenThrow(new LlmClientException("429q", null, new LlmProviderError(LlmErrorCategory.QUOTA_EXCEEDED, 429, "quota")))
                .thenThrow(new LlmClientException("429r", null, new LlmProviderError(LlmErrorCategory.RATE_LIMIT, 429, "rate")))
                .thenThrow(new LlmClientException("500", null, new LlmProviderError(LlmErrorCategory.PROVIDER_UNAVAILABLE, 503, "down")));

        assertEquals("API key inválida o no autorizada (401).", service.testConnection().message());
        assertEquals("Conexión rechazada por permisos del provider (403).", service.testConnection().message());
        assertEquals("Conexión fallida: cuota del provider agotada (429).", service.testConnection().message());
        assertEquals("Conexión limitada por rate-limit del provider (429).", service.testConnection().message());
        assertEquals("Proveedor LLM temporalmente no disponible (5xx).", service.testConnection().message());
    }

    @Test
    void testConnectionShouldStoreSanitizedMetricErrorCode() {
        llmProperties.setEnabled(true);
        llmProperties.setProvider("openai");
        llmProperties.setModel("gpt-4o-mini");
        llmProperties.setApiKey("sk-secret");
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("bad auth", null, new LlmProviderError(LlmErrorCategory.AUTH_ERROR, 401, "Bearer sk-secret")));

        service.testConnection();

        verify(llmUsageService).registerCall(
                any(),
                any(),
                any(),
                any(Integer.class),
                any(Integer.class),
                any(),
                any(Boolean.class),
                contains("TEST_CONNECTION|HTTP_401|AUTH_ERROR")
        );
    }

    @Test
    void updateConfigShouldRestoreOperationalStateAfterApiKeyDeletion() {
        LocalDateTime now = LocalDateTime.now();
        LlmConfig existingWithoutKey = new LlmConfig(
                UUID.randomUUID(),
                "openai",
                "gpt-4o-mini",
                "https://api.openai.com/v1/chat/completions",
                false,
                null,
                now,
                PromptBuilderService.defaultSystemPrompt(),
                "",
                "No tengo información asociada a eso.",
                RevealStrategy.PROGRESSIVE,
                6,
                0.4,
                350,
                true
        );
        UpdateLlmConfigRequest request = new UpdateLlmConfigRequest(
                "openai",
                "gpt-4.1-mini",
                null,
                true,
                "sk-restored",
                "",
                "responde corto",
                "No tengo información asociada a eso.",
                RevealStrategy.DIRECT,
                8,
                0.7,
                400,
                true,
                null
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(existingWithoutKey));
        when(llmConfigRepository.save(any(LlmConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LlmConfigResponse response = service.updateConfig(request);

        assertTrue(response.enabled());
        assertTrue(response.apiKeyConfigured());
        assertTrue(llmProperties.isEnabled());
        assertEquals("sk-restored", llmProperties.getApiKey());
    }

    @Test
    void loadPersistedConfigShouldNormalizeGroqBaseUrlInRuntimeWhenHostIsOpenAi() {
        LlmConfig config = new LlmConfig(
                UUID.randomUUID(),
                "groq",
                "llama-3.1-8b-instant",
                "https://api.openai.com/v1/chat/completions",
                true,
                llmApiKeyCipher.encrypt("gsk_test"),
                LocalDateTime.now(),
                PromptBuilderService.defaultSystemPrompt(),
                "",
                "No tengo información asociada a eso.",
                RevealStrategy.PROGRESSIVE,
                6,
                0.4,
                350,
                true
        );
        when(llmConfigRepository.findFirstByOrderByUpdatedAtDesc()).thenReturn(Optional.of(config));

        service.loadPersistedConfig();

        assertEquals("groq", llmProperties.getProvider());
        assertEquals("https://api.groq.com/openai/v1/chat/completions", llmProperties.getBaseUrl());
    }
}
