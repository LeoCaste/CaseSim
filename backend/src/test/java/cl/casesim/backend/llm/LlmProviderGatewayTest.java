package cl.casesim.backend.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmProviderGatewayTest {

    private LlmClient llmClient;
    private LlmProperties llmProperties;
    private PromptBuilderService promptBuilderService;
    private LlmErrorSanitizer llmErrorSanitizer;
    private LlmProviderGateway gateway;

    private PromptBuilderService.ClinicalPromptContext context;
    private List<LlmMessage> promptMessages;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmClient.class);
        llmProperties = new LlmProperties();
        llmProperties.setApiKey("test-api-key");
        llmProperties.setModel("test-model");
        llmProperties.setTemperature(0.5);
        llmProperties.setMaxTokens(200);
        llmProperties.setSystemPrompt("System prompt test");
        llmProperties.setPatientBehaviorRules("Behavior rules test");
        llmProperties.setRevealStrategy(RevealStrategy.PROGRESSIVE);
        promptBuilderService = mock(PromptBuilderService.class);
        llmErrorSanitizer = new LlmErrorSanitizer(llmProperties);

        gateway = new LlmProviderGateway(llmClient, llmProperties, promptBuilderService, llmErrorSanitizer);

        context = new PromptBuilderService.ClinicalPromptContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Test Case",
                "Patient",
                "30",
                "M",
                "Dolor abdominal",
                "Historial del caso",
                "No tengo información asociada a eso.",
                List.of("Rasgo 1", "Rasgo 2"),
                List.of("motivo_consulta: Dolor abdominal", "sintoma: fiebre"),
                "Mensaje inicial",
                "Contexto comunicable",
                "Enfermedad actual",
                "Antecedentes generales",
                "Hallazgos de examen",
                "Preocupado",
                "Breve",
                "Hablar en primera persona"
        );

        promptMessages = List.of(
                new LlmMessage("system", "System prompt here"),
                new LlmMessage("user", "User message here")
        );

        when(promptBuilderService.buildMessages(any(), any(), any(), any()))
                .thenReturn(List.of(
                        new LlmMessage("system", "Compact system prompt"),
                        new LlmMessage("user", "Compact user message")
                ));
    }

    @Test
    void llamadaPrincipalExitosa_retornaPrimarySuccess() {
        LlmResponse providerResponse = new LlmResponse("Respuesta exitosa del paciente", null, null);
        when(llmClient.generate(any())).thenReturn(providerResponse);

        LlmProviderGatewayResult result = gateway.executeCall(
                promptMessages, context, "¿Cómo te sientes?",
                "No tengo información.", "openai", "gpt-4o-mini"
        );

        assertTrue(result.primarySuccess());
        assertFalse(result.compactRetrySuccess());
        assertTrue(result.success());
        assertEquals("Respuesta exitosa del paciente", result.response());
        assertNotNull(result.providerResponse());
        assertNull(result.errorCause());
        assertNull(result.errorMessage());
        assertNull(result.originalException());
        verify(llmClient, times(1)).generate(any());
    }

    @Test
    void llamadaPrincipalFalla_compactRetryExitoso_retornaCompactRetrySuccess() {
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("respuesta vacia"))
                .thenReturn(new LlmResponse("Compact response del paciente", null, null));

        LlmProviderGatewayResult result = gateway.executeCall(
                promptMessages, context, "¿Cómo te sientes?",
                "No tengo información.", "openai", "gpt-4o-mini"
        );

        assertFalse(result.primarySuccess());
        assertTrue(result.compactRetrySuccess());
        assertTrue(result.success());
        assertEquals("Compact response del paciente", result.response());
        assertNotNull(result.providerResponse());
        verify(llmClient, times(2)).generate(any());
        verify(promptBuilderService, times(1)).buildMessages(any(), any(), any(), any());
    }

    @Test
    void llamadaPrincipalFalla_compactRetryFalla_retornaAllFailed() {
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("MODEL_INVALID"))
                .thenThrow(new LlmClientException("RETRY_FAILED"));

        LlmProviderGatewayResult result = gateway.executeCall(
                promptMessages, context, "¿Cómo te sientes?",
                "No tengo información.", "openai", "gpt-4o-mini"
        );

        assertFalse(result.primarySuccess());
        assertFalse(result.compactRetrySuccess());
        assertFalse(result.success());
        assertNull(result.response());
        assertNull(result.providerResponse());
        assertNotNull(result.errorCause());
        assertNotNull(result.errorMessage());
        assertNotNull(result.originalException());
        assertTrue(result.originalException() instanceof LlmClientException);
        verify(llmClient, times(2)).generate(any());
    }

    @Test
    void quotaExceeded_skipsCompactRetry_retornaAllFailed() {
        // LlmClientException with "status=429" in message classifies as QUOTA_EXCEEDED
        // But LlmClientException(message) creates LlmProviderError(UNKNOWN, null, message)
        // FallbackCauseClassifier uses providerError.category() first (UNKNOWN), not the message
        // So to truly test QUOTA_EXCEEDED skipping, we need the constructor that sets category
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException(
                        "Quota exceeded",
                        null,
                        new LlmProviderError(LlmErrorCategory.QUOTA_EXCEEDED, 429, "Quota exceeded")
                ));

        LlmProviderGatewayResult result = gateway.executeCall(
                promptMessages, context, "¿Cómo te sientes?",
                "No tengo información.", "openai", "gpt-4o-mini"
        );

        assertFalse(result.success());
        assertEquals("QUOTA_EXCEEDED", result.errorCause());
        assertNotNull(result.errorMessage());
        assertNotNull(result.originalException());
        // Compact retry was skipped, so only 1 call
        verify(llmClient, times(1)).generate(any());
        verify(promptBuilderService, never()).buildMessages(any(), any(), any(), any());
    }

    @Test
    void erroresSeSanitizanEnResultado() {
        // API key in error message should be sanitized
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("API key test-api-key is invalid"));

        LlmProviderGatewayResult result = gateway.executeCall(
                promptMessages, context, "¿Cómo te sientes?",
                "No tengo información.", "openai", "gpt-4o-mini"
        );

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
        assertFalse(result.errorMessage().contains("test-api-key"));
        assertNotNull(result.originalException());
    }

    @Test
    void compactRetryConservaComportamientoEsperado() {
        // Fails first, succeeds on retry
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("Error temporal"))
                .thenReturn(new LlmResponse("Respuesta del compact retry", null, null));

        LlmProviderGatewayResult result = gateway.executeCall(
                promptMessages, context, "¿Dolor?",
                "No sé.", "openai", "gpt-4o-mini"
        );

        assertTrue(result.compactRetrySuccess());
        assertEquals("Respuesta del compact retry", result.response());

        // Verify compact retry built a reduced prompt with limited facts
        ArgumentCaptor<PromptBuilderService.ClinicalPromptContext> contextCaptor =
                ArgumentCaptor.forClass(PromptBuilderService.ClinicalPromptContext.class);
        verify(promptBuilderService).buildMessages(contextCaptor.capture(), any(), any(), any());
        PromptBuilderService.ClinicalPromptContext compactContext = contextCaptor.getValue();
        assertNotNull(compactContext);
        List<String> compactFacts = compactContext.facts();
        assertNotNull(compactFacts);
        // compactFacts should be limited (contain only chiefComplaint + first fact)
        assertTrue(compactFacts.size() <= 2,
                "Compact retry should limit facts to chiefComplaint + first fact");
    }

    @Test
    void respuestaNulaEnLlmClient_manejadaComoVacia() {
        // simulate null LlmResponse from primary call
        when(llmClient.generate(any())).thenReturn(null);

        LlmProviderGatewayResult result = gateway.executeCall(
                promptMessages, context, "¿Cómo te sientes?",
                "No tengo información.", "openai", "gpt-4o-mini"
        );

        assertTrue(result.success());
        assertTrue(result.primarySuccess());
        assertEquals("", result.response());
    }

    @Test
    void respuestaNulaEnLlmClientCompactRetry_manejadaComoVacia() {
        // Primary fails, compact retry returns null
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("Error temporal"))
                .thenReturn(null);

        LlmProviderGatewayResult result = gateway.executeCall(
                promptMessages, context, "¿Cómo te sientes?",
                "No tengo información.", "openai", "gpt-4o-mini"
        );

        assertTrue(result.success());
        assertTrue(result.compactRetrySuccess());
        assertEquals("", result.response());
    }

    @Test
    void falloCompactRetry_retornaErrorPrimarioNoDeRetry() {
        // Primary fails with one error, retry fails with different error
        LlmClientException primaryError = new LlmClientException(
                "PRIMARY_ERROR",
                null,
                new LlmProviderError(LlmErrorCategory.TIMEOUT, null, "PRIMARY_ERROR")
        );
        LlmClientException retryError = new LlmClientException(
                "RETRY_ERROR",
                null,
                new LlmProviderError(LlmErrorCategory.INVALID_RESPONSE, null, "RETRY_ERROR")
        );
        when(llmClient.generate(any()))
                .thenThrow(primaryError)
                .thenThrow(retryError);

        LlmProviderGatewayResult result = gateway.executeCall(
                promptMessages, context, "¿Cómo te sientes?",
                "No tengo información.", "openai", "gpt-4o-mini"
        );

        assertFalse(result.success());
        // Should return PRIMARY error, not retry error
        assertEquals("TIMEOUT", result.errorCause());
        assertSame(primaryError, result.originalException());
    }

    @Test
    void compactRetryConservaJerarquiaInstitucionalYAdmin() {
        // Usar PromptBuilderService real para verificar jerarquía en compact retry
        PromptBuilderService realPromptBuilder = new PromptBuilderService();
        LlmProviderGateway gatewayWithRealBuilder = new LlmProviderGateway(
                llmClient, llmProperties, realPromptBuilder, llmErrorSanitizer
        );

        // Contexto clínico completo con 4 facts para verificar reducción
        PromptBuilderService.ClinicalPromptContext fullContext = new PromptBuilderService.ClinicalPromptContext(
                context.sessionId(), context.clinicalCaseId(), context.caseName(),
                context.patientName(), context.patientAge(), context.patientSex(),
                context.chiefComplaint(), context.caseHistory(), context.noInformationReply(),
                context.personalityTraits(),
                List.of("fact1: Dolor abdominal", "fact2: Fiebre de 38°C", "fact3: Tos seca", "fact4: Malestar general"),
                context.initialMessage(), context.broaderContext(), context.currentIllness(),
                context.generalBackground(), context.clinicalExamFindings(), context.tone(),
                context.detailLevel(), context.behaviorGuidelines()
        );

        // Config admin distintiva
        llmProperties.setSystemPrompt("ADMIN_SYSTEM_PROMPT_RETRY");
        llmProperties.setPatientBehaviorRules("ADMIN_BEHAVIOR_RULES_RETRY");

        // Simular fallo en primary, éxito en compact retry
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("Error temporal"))
                .thenReturn(new LlmResponse("Respuesta del compact retry", null, null));

        LlmProviderGatewayResult result = gatewayWithRealBuilder.executeCall(
                promptMessages, fullContext, "¿Dolor?",
                "No sé.", "openai", "gpt-4o-mini"
        );

        assertTrue(result.compactRetrySuccess());
        assertEquals("Respuesta del compact retry", result.response());

        // Capturar el LlmRequest del compact retry (segunda llamada a llmClient.generate)
        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient, times(2)).generate(requestCaptor.capture());
        List<LlmMessage> compactPrompt = requestCaptor.getValue().messages();
        String compactPromptContent = compactPrompt.get(0).content();

        // Verificar jerarquía completa
        assertTrue(compactPromptContent.contains("[CASESIM_INSTITUCIONAL_INMUTABLE]"),
                "Compact retry debe contener la capa institucional inmutable");
        assertTrue(compactPromptContent.contains("[CAPA_ADMIN_INSTITUCIONAL]"),
                "Compact retry debe contener la capa admin institucional");
        assertTrue(compactPromptContent.contains("[CAPA_ADMIN_REGLAS_PACIENTE]"),
                "Compact retry debe contener la capa admin reglas");
        assertTrue(compactPromptContent.contains("[CAPA_PROFESOR_CONTEXTO_CLINICO]"),
                "Compact retry debe contener la capa profesor/caso");
        assertTrue(compactPromptContent.contains("[POLITICA_ROL_Y_NO_DIAGNOSTICO]"),
                "Compact retry debe contener la política de rol y no diagnóstico");
        assertTrue(compactPromptContent.contains("[REGLA_REVELACION]"),
                "Compact retry debe contener la regla de revelación");

        // Verificar contenido admin
        assertTrue(compactPromptContent.contains("ADMIN_SYSTEM_PROMPT_RETRY"),
                "Compact retry debe contener el systemPrompt del admin");
        assertTrue(compactPromptContent.contains("ADMIN_BEHAVIOR_RULES_RETRY"),
                "Compact retry debe contener las reglas de comportamiento del admin");

        // Verificar orden correcto
        int idxInmutable = compactPromptContent.indexOf("[CASESIM_INSTITUCIONAL_INMUTABLE]");
        int idxAdminInst = compactPromptContent.indexOf("[CAPA_ADMIN_INSTITUCIONAL]");
        int idxAdminReglas = compactPromptContent.indexOf("[CAPA_ADMIN_REGLAS_PACIENTE]");
        int idxProfesor = compactPromptContent.indexOf("[CAPA_PROFESOR_CONTEXTO_CLINICO]");

        assertTrue(idxInmutable < idxAdminInst,
                "Compact retry: inmutable antes que admin institucional");
        assertTrue(idxAdminInst < idxAdminReglas,
                "Compact retry: admin institucional antes que admin reglas");
        assertTrue(idxAdminReglas < idxProfesor,
                "Compact retry: admin reglas antes que profesor/caso");

        // Verificar que los facts están reducidos (compact retry reduce a máximo 2)
        assertTrue(compactPromptContent.contains("motivo_consulta:"),
                "Compact retry debe contener motivo de consulta");
        long factCount = compactPromptContent.lines()
                .filter(line -> line.contains("fact1:") || line.contains("fact2:")
                        || line.contains("fact3:") || line.contains("fact4:"))
                .count();
        assertTrue(factCount <= 2,
                "Compact retry debe reducir facts a máximo 2");
    }
}
