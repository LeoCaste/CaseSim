package cl.casesim.backend.llm;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
                "sk-1234567890",
                LocalDateTime.now()
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
        when(llmClient.generateChatCompletion(any())).thenReturn("pong");

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
                "sk-new"
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
}
