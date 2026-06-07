package cl.casesim.backend.llm;

import cl.casesim.backend.sessions.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para {@link PatientPromptAssemblyService}.
 * <p>
 * Verifica que el ensamblaje de mensajes delega correctamente en
 * {@link PromptBuilderService}, preserva historial, userMessage y contexto,
 * y calcula métricas de tokens y caracteres del prompt.
 * </p>
 */
class PatientPromptAssemblyServiceTest {

    private PromptBuilderService promptBuilderService;
    private LlmInteractionMetricsService llmInteractionMetricsService;
    private PatientPromptAssemblyService assemblyService;

    private PromptBuilderService.ClinicalPromptContext context;
    private List<ChatMessage> history;
    private String userMessage;
    private PromptBuilderService.PatientBehaviorConfig behaviorConfig;

    @BeforeEach
    void setUp() {
        promptBuilderService = mock(PromptBuilderService.class);
        llmInteractionMetricsService = mock(LlmInteractionMetricsService.class);

        assemblyService = new PatientPromptAssemblyService(
                promptBuilderService,
                llmInteractionMetricsService
        );

        context = new PromptBuilderService.ClinicalPromptContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Caso de prueba",
                "Paciente Test",
                "35",
                "M",
                "Dolor de cabeza",
                "Historia del caso",
                "No tengo información.",
                List.of("Rasgo 1", "Rasgo 2"),
                List.of("fact_initial: Dolor de cabeza"),
                "Mensaje inicial",
                "Contexto comunicable",
                "Enfermedad actual",
                "Antecedentes generales",
                "Hallazgos de examen",
                "preocupado",
                "breve",
                "Guias de conducta"
        );

        history = List.of(
                new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), "USER", "Hola", 1, LocalDateTime.now()),
                new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), "ASSISTANT", "Respuesta anterior", 2, LocalDateTime.now())
        );

        userMessage = "¿Cómo te sientes?";

        behaviorConfig = new PromptBuilderService.PatientBehaviorConfig(
                "System prompt personalizado",
                "Reglas de comportamiento",
                "No lo sé.",
                RevealStrategy.PROGRESSIVE
        );
    }

    @Test
    void construyeMensajesUsandoPromptBuilderService() {
        List<LlmMessage> expectedMessages = List.of(
                new LlmMessage("system", "System prompt"),
                new LlmMessage("user", userMessage)
        );
        when(promptBuilderService.buildMessages(any(), any(), any(), any()))
                .thenReturn(expectedMessages);
        when(llmInteractionMetricsService.estimateTokens(anyString()))
                .thenReturn(10);

        PatientPromptAssemblyService.PromptAssemblyResult result = assemblyService.assemblePrompt(
                context, history, userMessage, behaviorConfig
        );

        assertNotNull(result);
        assertSame(expectedMessages, result.promptMessages(),
                "Debe retornar exactamente los mensajes construidos por PromptBuilderService");
        assertEquals(20, result.estimatedPromptTokens(),
                "Debe sumar los tokens estimados de cada mensaje (10 + 10)");
        // "System prompt" = 13 chars, "¿Cómo te sientes?" = 17 chars
        assertEquals(30, result.promptChars(),
                "Debe sumar los caracteres de cada mensaje (13 + 17 = 30)");

        verify(promptBuilderService, times(1)).buildMessages(context, history, userMessage, behaviorConfig);
        verify(llmInteractionMetricsService, times(1)).estimateTokens("System prompt");
        verify(llmInteractionMetricsService, times(1)).estimateTokens(userMessage);
    }

    @Test
    void preservaHistorialRecibido() {
        when(promptBuilderService.buildMessages(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    List<ChatMessage> histParam = invocation.getArgument(1);
                    List<LlmMessage> messages = new ArrayList<>();
                    messages.add(new LlmMessage("system", "Test"));
                    for (ChatMessage msg : histParam) {
                        messages.add(new LlmMessage(
                                "ASSISTANT".equalsIgnoreCase(msg.getRol()) ? "assistant" : "user",
                                msg.getContenido()
                        ));
                    }
                    messages.add(new LlmMessage("user", invocation.getArgument(2)));
                    return messages;
                });
        when(llmInteractionMetricsService.estimateTokens(anyString())).thenReturn(1);

        PatientPromptAssemblyService.PromptAssemblyResult result = assemblyService.assemblePrompt(
                context, history, userMessage, behaviorConfig
        );

        List<LlmMessage> messages = result.promptMessages();
        assertEquals(4, messages.size(), "system + 2 history + user = 4 mensajes");

        assertEquals("user", messages.get(1).role(), "Primer mensaje de historial debe ser user");
        assertEquals("Hola", messages.get(1).content(), "Contenido del primer mensaje de historial preservado");
        assertEquals("assistant", messages.get(2).role(), "Segundo mensaje de historial debe ser assistant");
        assertEquals("Respuesta anterior", messages.get(2).content(), "Contenido del segundo mensaje preservado");
        assertEquals("user", messages.get(3).role(), "Último mensaje debe ser user");
        assertEquals(userMessage, messages.get(3).content(), "Último mensaje debe ser el userMessage actual");
    }

    @Test
    void preservaUserMessage() {
        when(promptBuilderService.buildMessages(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String msgParam = invocation.getArgument(2);
                    return List.of(new LlmMessage("user", msgParam));
                });
        when(llmInteractionMetricsService.estimateTokens(anyString())).thenReturn(1);

        PatientPromptAssemblyService.PromptAssemblyResult result = assemblyService.assemblePrompt(
                context, history, userMessage, behaviorConfig
        );

        String content = result.promptMessages().get(0).content();
        assertEquals(userMessage, content, "userMessage debe ser preservado exactamente");
    }

    @Test
    void noAlteraClinicalPromptContext() {
        when(promptBuilderService.buildMessages(any(), any(), any(), any()))
                .thenReturn(List.of(new LlmMessage("system", "test")));
        when(llmInteractionMetricsService.estimateTokens(anyString())).thenReturn(1);

        // Capturar el contexto recibido por PromptBuilderService
        assemblyService.assemblePrompt(context, history, userMessage, behaviorConfig);

        ArgumentCaptor<PromptBuilderService.ClinicalPromptContext> contextCaptor =
                ArgumentCaptor.forClass(PromptBuilderService.ClinicalPromptContext.class);
        verify(promptBuilderService).buildMessages(contextCaptor.capture(), any(), any(), any());

        PromptBuilderService.ClinicalPromptContext capturedContext = contextCaptor.getValue();
        assertSame(context, capturedContext,
                "El ClinicalPromptContext debe pasarse sin modificación al PromptBuilderService");
    }

    @Test
    void noEliminaNoInformationReply() {
        when(promptBuilderService.buildMessages(any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    PromptBuilderService.PatientBehaviorConfig cfg = invocation.getArgument(3);
                    return List.of(
                            new LlmMessage("system", "noInfo: " + cfg.noInformationReply())
                    );
                });
        when(llmInteractionMetricsService.estimateTokens(anyString())).thenReturn(1);

        PatientPromptAssemblyService.PromptAssemblyResult result = assemblyService.assemblePrompt(
                context, history, userMessage, behaviorConfig
        );

        assertTrue(result.promptMessages().get(0).content().contains("No lo sé."),
                "noInformationReply debe preservarse en el comportamiento");
    }

    @Test
    void noCambiaOrdenEsperadoDeMensajes() {
        List<LlmMessage> expectedOrder = List.of(
                new LlmMessage("system", "capa1"),
                new LlmMessage("system", "noInfo"),
                new LlmMessage("system", "noInfoGuard"),
                new LlmMessage("system", "precedence"),
                new LlmMessage("user", "h1"),
                new LlmMessage("assistant", "a1"),
                new LlmMessage("user", userMessage)
        );
        when(promptBuilderService.buildMessages(any(), any(), any(), any()))
                .thenReturn(expectedOrder);
        when(llmInteractionMetricsService.estimateTokens(anyString())).thenReturn(1);

        PatientPromptAssemblyService.PromptAssemblyResult result = assemblyService.assemblePrompt(
                context, history, userMessage, behaviorConfig
        );

        List<LlmMessage> actual = result.promptMessages();
        assertEquals(expectedOrder.size(), actual.size(), "Misma cantidad de mensajes");
        for (int i = 0; i < expectedOrder.size(); i++) {
            assertEquals(expectedOrder.get(i).role(), actual.get(i).role(),
                    "Rol del mensaje " + i + " debe coincidir");
            assertEquals(expectedOrder.get(i).content(), actual.get(i).content(),
                    "Contenido del mensaje " + i + " debe coincidir");
        }
    }

    @Test
    void delegaCorrectamenteAPromptBuilderService() {
        when(promptBuilderService.buildMessages(any(), any(), any(), any()))
                .thenReturn(List.of(new LlmMessage("system", "test")));
        when(llmInteractionMetricsService.estimateTokens(anyString())).thenReturn(1);

        assemblyService.assemblePrompt(context, history, userMessage, behaviorConfig);

        verify(promptBuilderService, times(1))
                .buildMessages(eq(context), eq(history), eq(userMessage), eq(behaviorConfig));
    }

    @Test
    void calculaTokensYCaracteresCorrectamente() {
        List<LlmMessage> messages = List.of(
                new LlmMessage("system", "mensaje1"),   // 8 chars
                new LlmMessage("user", "mensaje2 largo")  // 14 chars ("mensaje2 largo")
        );
        when(promptBuilderService.buildMessages(any(), any(), any(), any()))
                .thenReturn(messages);
        when(llmInteractionMetricsService.estimateTokens("mensaje1")).thenReturn(5);
        when(llmInteractionMetricsService.estimateTokens("mensaje2 largo")).thenReturn(8);

        PatientPromptAssemblyService.PromptAssemblyResult result = assemblyService.assemblePrompt(
                context, history, userMessage, behaviorConfig
        );

        assertEquals(13, result.estimatedPromptTokens(), "5 + 8 = 13 tokens");
        assertEquals(22, result.promptChars(), "8 + 14 = 22 caracteres");
    }

    @Test
    void resultadoContieneCamposNoNulos() {
        when(promptBuilderService.buildMessages(any(), any(), any(), any()))
                .thenReturn(List.of(new LlmMessage("system", "test")));
        when(llmInteractionMetricsService.estimateTokens(anyString())).thenReturn(1);

        PatientPromptAssemblyService.PromptAssemblyResult result = assemblyService.assemblePrompt(
                context, history, userMessage, behaviorConfig
        );

        assertNotNull(result.promptMessages());
        assertFalse(result.promptMessages().isEmpty());
        assertTrue(result.estimatedPromptTokens() > 0);
        assertTrue(result.promptChars() > 0);
    }

    @Test
    void manejaMensajesNulosEnPrompt() {
        List<LlmMessage> messagesWithNull = new ArrayList<>();
        messagesWithNull.add(new LlmMessage("system", null));
        messagesWithNull.add(new LlmMessage("user", "texto válido"));
        when(promptBuilderService.buildMessages(any(), any(), any(), any()))
                .thenReturn(messagesWithNull);
        when(llmInteractionMetricsService.estimateTokens(anyString())).thenReturn(1);
        when(llmInteractionMetricsService.estimateTokens(null)).thenReturn(0);

        PatientPromptAssemblyService.PromptAssemblyResult result = assemblyService.assemblePrompt(
                context, history, userMessage, behaviorConfig
        );

        // null → 0 chars + 0 tokens, "texto válido" → 12 chars + 1 token
        assertEquals(1, result.estimatedPromptTokens(), "Mensaje nulo aporta 0 tokens, el otro aporta 1");
        assertEquals(12, result.promptChars(), "Mensaje nulo aporta 0 chars, 'texto válido' aporta 12 chars");
    }
}
