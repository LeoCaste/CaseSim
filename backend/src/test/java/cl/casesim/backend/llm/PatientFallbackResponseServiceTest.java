package cl.casesim.backend.llm;

import cl.casesim.backend.sessions.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientFallbackResponseServiceTest {

    private final ResponseSafetyFilter responseSafetyFilter = mock(ResponseSafetyFilter.class);
    private PatientFallbackResponseService service;

    @BeforeEach
    void setUp() {
        PatientResponseSafetyService safetyService = new PatientResponseSafetyService(responseSafetyFilter);
        service = new PatientFallbackResponseService(safetyService);
        when(responseSafetyFilter.applyOrFallback(anyString(), anyBoolean(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void greetingBuildsExactPatientGreetingWithChiefComplaint() {
        PromptBuilderService.ClinicalPromptContext context = context("María", "Dolor abdominal", List.of());

        String response = service.buildContextualPatientFallback(context, "Hola", "No tengo información asociada a eso.");

        assertEquals("Hola, soy María. Dolor abdominal", response);
    }

    @Test
    void temporalQuestionUsesFirstTemporalFact() {
        PromptBuilderService.ClinicalPromptContext context = context(
                "María",
                "Dolor abdominal",
                List.of("[categoria=sintoma] intensidad: Dolor moderado", "[categoria=tiempo] inicio: Desde hace dos días")
        );

        String response = service.buildContextualPatientFallback(context, "¿Desde cuándo le pasa?", "No tengo información asociada a eso.");

        assertEquals("Desde hace dos días", response);
    }

    @Test
    void nonTemporalQuestionUsesFirstUsableFact() {
        PromptBuilderService.ClinicalPromptContext context = context(
                "María",
                "Dolor abdominal",
                List.of("[categoria=motivo] motivo: Dolor abdominal", "[categoria=sintoma] asociado: Náuseas")
        );

        String response = service.buildContextualPatientFallback(context, "¿Qué síntomas siente ahora?", "No tengo información asociada a eso.");

        assertEquals("Náuseas", response);
    }

    @Test
    void withoutUsableFactsUsesChiefComplaintWhenPresent() {
        PromptBuilderService.ClinicalPromptContext context = context(
                "María",
                "Dolor abdominal",
                List.of("[categoria=motivo] motivo: Dolor abdominal")
        );

        String response = service.buildContextualPatientFallback(context, "¿Qué síntomas siente ahora?", "No tengo información asociada a eso.");

        assertEquals("Dolor abdominal", response);
    }

    @Test
    void withoutChiefComplaintUsesNoInfoResponse() {
        PromptBuilderService.ClinicalPromptContext context = context("María", null, List.of());

        String response = service.buildContextualPatientFallback(context, "¿Qué siente?", "No tengo información asociada a eso.");

        assertEquals("No tengo información asociada a eso.", response);
    }

    @Test
    void quotaMessageUsesExactQuotaFallbackResponse() {
        PromptBuilderService.ClinicalPromptContext context = context("María", "Dolor abdominal", List.of());

        String response = service.buildContextualPatientFallback(context, "quota", "No tengo información asociada a eso.");

        assertEquals("Estoy con alta demanda en este momento. Si te parece, continuamos con preguntas concretas de síntomas, tiempos o antecedentes mientras se restablece el servicio.", response);
    }

    @Test
    void consecutiveRepetitionSearchesAlternativeAndAppliesSafety() {
        PromptBuilderService.ClinicalPromptContext context = context(
                "María",
                "Dolor abdominal",
                List.of("[categoria=motivo] motivo: Dolor abdominal", "[categoria=sintoma] asociado: Náuseas")
        );
        List<ChatMessage> history = List.of(assistant("Dolor abdominal"));

        String response = service.avoidConsecutiveRepetition(
                "Dolor abdominal",
                history,
                "¿Qué más siente?",
                context,
                "No tengo información asociada a eso.",
                true
        );

        assertEquals("Náuseas", response);
        verify(responseSafetyFilter).applyOrFallback("Náuseas", true, "No tengo información asociada a eso.");
    }

    @Test
    void withoutAlternativePreservesOriginalResponseWhenGreetingTurn() {
        PromptBuilderService.ClinicalPromptContext context = context(
                "María",
                "Dolor abdominal",
                List.of("[categoria=sintoma] asociado: Náuseas")
        );
        List<ChatMessage> history = List.of(assistant("Dolor abdominal"));

        String response = service.avoidConsecutiveRepetition(
                "Dolor abdominal",
                history,
                "Hola",
                context,
                "No tengo información asociada a eso.",
                true
        );

        assertEquals("Dolor abdominal", response);
    }

    @Test
    void noInfoResponseIsUsedAsFinalAlternativeFallback() {
        when(responseSafetyFilter.applyOrFallback("Dato sensible", true, "No tengo información asociada a eso."))
                .thenReturn("No tengo información asociada a eso.");
        PromptBuilderService.ClinicalPromptContext context = context(
                "María",
                "Dolor abdominal",
                List.of("[categoria=sintoma] reservado: Dato sensible")
        );
        List<ChatMessage> history = List.of(assistant("Dolor abdominal"));

        String response = service.avoidConsecutiveRepetition(
                "Dolor abdominal",
                history,
                "¿Qué más siente?",
                context,
                "No tengo información asociada a eso.",
                true
        );

        assertEquals("No tengo información asociada a eso.", response);
    }

    @Test
    void nullNoInfoResponseFallsBackToDefaultNoInfoForRepetitionSafety() {
        PromptBuilderService.ClinicalPromptContext context = context(
                "María",
                "Dolor abdominal",
                List.of("[categoria=sintoma] asociado: Náuseas")
        );
        List<ChatMessage> history = List.of(assistant("Dolor abdominal"));

        service.avoidConsecutiveRepetition("Dolor abdominal", history, "¿Qué más siente?", context, null, true);

        verify(responseSafetyFilter).applyOrFallback("Náuseas", true, "No tengo información asociada a eso.");
    }

    private PromptBuilderService.ClinicalPromptContext context(String patientName, String chiefComplaint, List<String> facts) {
        return new PromptBuilderService.ClinicalPromptContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Caso",
                patientName,
                "24",
                "F",
                chiefComplaint,
                "Historia",
                null,
                List.of(),
                facts,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private ChatMessage assistant(String content) {
        return new ChatMessage(UUID.randomUUID(), UUID.randomUUID(), "ASSISTANT", content, 1, LocalDateTime.now());
    }
}
