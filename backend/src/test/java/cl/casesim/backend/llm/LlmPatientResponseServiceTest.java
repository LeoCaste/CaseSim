package cl.casesim.backend.llm;

import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseFact;
import cl.casesim.backend.clinicalcases.ClinicalCaseFactRepository;
import cl.casesim.backend.clinicalcases.ClinicalCasePersonalityRepository;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.sessions.ChatMessage;
import cl.casesim.backend.sessions.ChatMessageRepository;
import cl.casesim.backend.sessions.SessionRevealedFact;
import cl.casesim.backend.sessions.SessionRevealedFactRepository;
import cl.casesim.backend.sessions.SimulationSession;
import cl.casesim.backend.sessions.SimulationSessionRepository;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.reset;

class LlmPatientResponseServiceTest {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final ClinicalCaseRepository clinicalCaseRepository = mock(ClinicalCaseRepository.class);
    private final ClinicalCaseFactRepository clinicalCaseFactRepository = mock(ClinicalCaseFactRepository.class);
    private final ClinicalCasePersonalityRepository clinicalCasePersonalityRepository = mock(ClinicalCasePersonalityRepository.class);
    private final SimulationActivityRepository simulationActivityRepository = mock(SimulationActivityRepository.class);
    private final SimulationSessionRepository simulationSessionRepository = mock(SimulationSessionRepository.class);
    private final SessionRevealedFactRepository sessionRevealedFactRepository = mock(SessionRevealedFactRepository.class);
    private final LlmUsageRepository llmUsageRepository = mock(LlmUsageRepository.class);
    private final ResponseSafetyFilter responseSafetyFilter = mock(ResponseSafetyFilter.class);

    private final PromptBuilderService promptBuilderService = new PromptBuilderService();
    private final LlmUsageService llmUsageService = new LlmUsageService(
            llmUsageRepository,
            new BigDecimal("0.00015"),
            new BigDecimal("0.00060"),
            new BigDecimal("950")
    );

    private LlmPatientResponseService service;
    private LlmProperties properties;
    private SimulationSession session;
    private SimulationActivity activity;
    private ClinicalCase clinicalCase;
    private ClinicalCaseFact level1Fact;
    private ClinicalCaseFact level2Fact;
    private ClinicalCaseFact level3Fact;

    @BeforeEach
    void setUp() {
        properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-api-key");

        service = new LlmPatientResponseService(
                properties,
                llmClient,
                promptBuilderService,
                responseSafetyFilter,
                chatMessageRepository,
                llmUsageService,
                simulationActivityRepository,
                simulationSessionRepository,
                clinicalCaseRepository,
                clinicalCaseFactRepository,
                clinicalCasePersonalityRepository,
                sessionRevealedFactRepository
        );

        UUID sessionId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        session = new SimulationSession(
                sessionId,
                activityId,
                UUID.randomUUID(),
                "EN_CURSO",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        activity = new SimulationActivity(
                activityId,
                UUID.randomUUID(),
                caseId,
                "Actividad",
                null,
                "FORMATIVO",
                false,
                null,
                true,
                UUID.randomUUID(),
                LocalDateTime.now()
        );

        clinicalCase = new ClinicalCase(
                caseId,
                "Caso",
                "Historia",
                "Paciente",
                24,
                "F",
                "Dolor abdominal",
                "No tengo información asociada a eso.",
                true,
                UUID.randomUUID(),
                LocalDateTime.now()
        );

        level1Fact = new ClinicalCaseFact(UUID.randomUUID(), caseId, "GENERAL", "motivo", "Dolor abdominal", 1, null, false, 0);
        level2Fact = new ClinicalCaseFact(UUID.randomUUID(), caseId, "GENERAL", "fiebre", "Tengo fiebre desde ayer", 2, null, false, 1);
        level3Fact = new ClinicalCaseFact(UUID.randomUUID(), caseId, "GENERAL", "antecedente", "Tuve cirugía hace 2 años", 3, null, false, 2);

        when(chatMessageRepository.findBySesionIdOrderByNumeroTurnoDesc(any(), any())).thenReturn(List.of());
        when(llmUsageRepository.save(any(LlmUsage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(simulationActivityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(clinicalCaseRepository.findById(clinicalCase.getId())).thenReturn(Optional.of(clinicalCase));
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(clinicalCase.getId())).thenReturn(List.of(level1Fact, level2Fact, level3Fact));
        when(clinicalCasePersonalityRepository.findByCasoId(clinicalCase.getId())).thenReturn(List.of());
        when(responseSafetyFilter.applyOrFallback(anyString(), anyBoolean(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(llmClient.generate(any())).thenReturn(new LlmResponse("respuesta segura", null, null));
    }

    @Test
    void seleccionInicialIncluyeSoloFactsNivelUno() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());

        service.generateResponse(session, "Hola");

        String contextualPrompt = getContextualPrompt();
        assertTrue(contextualPrompt.contains("Motivo de consulta principal: Dolor abdominal"));
        assertTrue(contextualPrompt.contains("motivo: Dolor abdominal"));
        assertFalse(contextualPrompt.contains("fiebre: Tengo fiebre desde ayer"));
        assertFalse(contextualPrompt.contains("antecedente: Tuve cirugía hace 2 años"));
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
    }

    @Test
    void anteErrorProveedorIntentaReintentoCompactoAntesDeFallbackTecnico() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("respuesta vacia"))
                .thenReturn(new LlmResponse("Como paciente, tengo dolor abdominal desde ayer.", null, null));

        String response = service.generateResponse(session, "Hola");

        assertTrue(response.contains("dolor abdominal"));
        verify(llmClient, times(2)).generate(any());

        ArgumentCaptor<LlmUsage> usageCaptor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository, atLeastOnce()).save(usageCaptor.capture());
        LlmUsage usage = usageCaptor.getValue();
        assertFalse(readBooleanField(usage, "fallbackUsed"));
        assertEquals(null, readStringField(usage, "error"));
    }

    @Test
    void preguntaRelevanteRevelaFactNivelDos() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(session.getId(), level2Fact.getId())).thenReturn(false);

        service.generateResponse(session, "¿Tiene fiebre?");

        String contextualPrompt = getContextualPrompt();
        assertTrue(contextualPrompt.contains("fiebre: Tengo fiebre desde ayer"));

        ArgumentCaptor<SessionRevealedFact> captor = ArgumentCaptor.forClass(SessionRevealedFact.class);
        verify(sessionRevealedFactRepository).save(captor.capture());
        assertTrue(captor.getValue().getFactId().equals(level2Fact.getId()));
    }

    @Test
    void factYaReveladoNoDuplicaInsercion() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of(level2Fact.getId()));

        service.generateResponse(session, "¿Tiene fiebre?");

        String contextualPrompt = getContextualPrompt();
        assertTrue(contextualPrompt.contains("fiebre: Tengo fiebre desde ayer"));
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
    }

    @Test
    void preguntaIrrelevanteNoRevelaFactsNuevos() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());

        service.generateResponse(session, "¿Cómo está el clima?");

        String contextualPrompt = getContextualPrompt();
        assertFalse(contextualPrompt.contains("fiebre: Tengo fiebre desde ayer"));
        assertFalse(contextualPrompt.contains("antecedente: Tuve cirugía hace 2 años"));
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
    }

    @Test
    void revealStrategyDirectPermiteRevelacionMayorTemprana() {
        properties.setRevealStrategy(RevealStrategy.DIRECT);
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(session.getId(), level3Fact.getId())).thenReturn(false);

        service.generateResponse(session, "¿Tuvo cirugía?");

        String contextualPrompt = getContextualPrompt();
        assertTrue(contextualPrompt.contains("antecedente: Tuve cirugía hace 2 años"));
    }

    @Test
    void revealStrategyRestrictiveExigeMayorEspecificidad() {
        properties.setRevealStrategy(RevealStrategy.RESTRICTIVE);
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());

        service.generateResponse(session, "¿Tiene fiebre?");

        String contextualPrompt = getContextualPrompt();
        assertFalse(contextualPrompt.contains("fiebre: Tengo fiebre desde ayer"));
    }

    @Test
    void triggerDelFactPermiteMatchAunqueNoAparezcaEnContenido() {
        ClinicalCaseFact triggerFact = new ClinicalCaseFact(
                UUID.randomUUID(),
                clinicalCase.getId(),
                "GENERAL",
                "habito",
                "Solo tomo agua ocasionalmente",
                2,
                "[\"sed\",\"hidratacion\"]",
                false,
                3
        );

        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(clinicalCase.getId()))
                .thenReturn(List.of(level1Fact, triggerFact));
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(session.getId(), triggerFact.getId())).thenReturn(false);

        service.generateResponse(session, "¿Tiene sed?");

        String contextualPrompt = getContextualPrompt();
        assertTrue(contextualPrompt.contains("habito: Solo tomo agua ocasionalmente"));
    }

    @Test
    void safetyFilterDeshabilitadoSigueAplicandoFiltroBase() {
        properties.setEnabledSafetyFilter(false);
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(responseSafetyFilter.applyOrFallback(anyString(), eq(false), anyString())).thenReturn(ResponseSafetyFilter.SAFE_FALLBACK);
        when(llmClient.generate(any())).thenReturn(new LlmResponse("Soy una IA", null, null));

        String response = service.generateResponse(session, "hola");

        assertEquals(ResponseSafetyFilter.SAFE_FALLBACK, response);
    }

    @Test
    void prioridadNoInfoCasoSobreAdminEnPrompt() {
        properties.setNoInfoResponse("NO_INFO_ADMIN");
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());

        service.generateResponse(session, "¿Qué alergias tiene?");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).generate(requestCaptor.capture());
        String noInfoInstruction = requestCaptor.getValue().messages().get(1).content();
        assertTrue(noInfoInstruction.contains("No tengo información asociada a eso."));
        assertFalse(noInfoInstruction.contains("NO_INFO_ADMIN"));
    }

    @Test
    void siLlmDeshabilitadoRetornaFallbackTecnico() {
        properties.setEnabled(false);

        String response = service.generateResponse(session, "hola");

        assertEquals("Perdón, me cuesta responder en este momento. ¿Podrías repetir tu pregunta?", response);
    }

    @Test
    void promptIncluyeReglaParaNoUsarNoInfoSiHayFactsDisponibles() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());

        service.generateResponse(session, "hola");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).generate(requestCaptor.capture());
        String noInfoGuard = requestCaptor.getValue().messages().get(2).content();
        String systemLayeredPrompt = requestCaptor.getValue().messages().get(0).content();

        assertTrue(noInfoGuard.contains("NO uses la respuesta sin información"));
        assertTrue(systemLayeredPrompt.contains("motivo: Dolor abdominal"));
    }

    @Test
    void dosSesionesConCasosDistintosConstruyenPromptsDistintos() {
        UUID activityId2 = UUID.randomUUID();
        UUID caseId2 = UUID.randomUUID();
        SimulationSession session2 = new SimulationSession(
                UUID.randomUUID(),
                activityId2,
                UUID.randomUUID(),
                "EN_CURSO",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        SimulationActivity activity2 = new SimulationActivity(
                activityId2,
                UUID.randomUUID(),
                caseId2,
                "Actividad 2",
                null,
                "FORMATIVO",
                false,
                null,
                true,
                UUID.randomUUID(),
                LocalDateTime.now()
        );

        ClinicalCase clinicalCase2 = new ClinicalCase(
                caseId2,
                "Caso 2",
                "Historia 2",
                "Paciente 2",
                50,
                "M",
                "Dolor torácico",
                "No tengo información asociada a eso.",
                true,
                UUID.randomUUID(),
                LocalDateTime.now()
        );

        ClinicalCaseFact fact2 = new ClinicalCaseFact(
                UUID.randomUUID(),
                caseId2,
                "GENERAL",
                "dolor_toracico",
                "Siento una presión en el pecho desde anoche",
                1,
                null,
                false,
                0
        );

        when(simulationActivityRepository.findById(activityId2)).thenReturn(Optional.of(activity2));
        when(clinicalCaseRepository.findById(caseId2)).thenReturn(Optional.of(clinicalCase2));
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(caseId2)).thenReturn(List.of(fact2));
        when(clinicalCasePersonalityRepository.findByCasoId(caseId2)).thenReturn(List.of());
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session2.getId())).thenReturn(Set.of());

        service.generateResponse(session, "hola caso 1");
        String promptCase1 = getLastContextualPrompt();

        service.generateResponse(session2, "hola caso 2");
        String promptCase2 = getLastContextualPrompt();

        assertTrue(promptCase1.contains("Caso"));
        assertTrue(promptCase2.contains("Caso 2"));
        assertTrue(promptCase1.contains("motivo: Dolor abdominal"));
        assertTrue(promptCase2.contains("dolor_toracico: Siento una presión en el pecho desde anoche"));
        assertNotEquals(promptCase1, promptCase2);
    }

    @Test
    void saludoConContextoValidoNoCaeEnFallbackTecnicoSiProveedorFalla() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(clinicalCase.getId())).thenReturn(List.of());
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("MODEL_INVALID"))
                .thenThrow(new LlmClientException("MODEL_INVALID"));

        String response = service.generateResponse(session, "Hola");

        assertTrue(response.contains("Dolor abdominal") || response.contains("Paciente"));
        assertFalse(response.contains("Perdón, me cuesta responder"));
    }

    @Test
    void segundoTurnoConPreguntaDirigidaNoRepiteLiteralSiHayFactDisponible() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(session.getId(), level2Fact.getId())).thenReturn(false);

        ChatMessage previousUser = new ChatMessage(
                UUID.randomUUID(),
                session.getId(),
                "USER",
                "Hola",
                1,
                LocalDateTime.now().minusMinutes(1)
        );
        ChatMessage previousAssistant = new ChatMessage(
                UUID.randomUUID(),
                session.getId(),
                "ASSISTANT",
                "Dolor abdominal",
                2,
                LocalDateTime.now().minusSeconds(30)
        );
        when(chatMessageRepository.findBySesionIdOrderByNumeroTurnoDesc(any(), any()))
                .thenReturn(List.of(previousAssistant, previousUser));
        when(llmClient.generate(any())).thenReturn(new LlmResponse("Dolor abdominal", null, null));

        String response = service.generateResponse(session, "¿Hace cuánto tiene fiebre?");

        assertNotEquals("Dolor abdominal", response);
        assertEquals("Tengo fiebre desde ayer", response);
    }

    @Test
    void historialRecienteSeIncluyeEnPromptYAfectaSalida() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(session.getId(), level2Fact.getId())).thenReturn(false);

        ChatMessage previousUser = new ChatMessage(
                UUID.randomUUID(),
                session.getId(),
                "USER",
                "Hola",
                1,
                LocalDateTime.now().minusMinutes(1)
        );
        ChatMessage previousAssistant = new ChatMessage(
                UUID.randomUUID(),
                session.getId(),
                "ASSISTANT",
                "Dolor abdominal",
                2,
                LocalDateTime.now().minusSeconds(30)
        );
        when(chatMessageRepository.findBySesionIdOrderByNumeroTurnoDesc(any(), any()))
                .thenReturn(List.of(previousAssistant, previousUser));
        when(llmClient.generate(any())).thenReturn(new LlmResponse("Dolor abdominal", null, null));

        String response = service.generateResponse(session, "¿Desde cuándo?");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient, atLeastOnce()).generate(requestCaptor.capture());
        List<LlmMessage> messages = requestCaptor.getValue().messages();

        assertTrue(messages.stream().anyMatch(msg -> "assistant".equals(msg.role()) && "Dolor abdominal".equals(msg.content())));
        assertTrue(messages.stream().anyMatch(msg -> "user".equals(msg.role()) && "Hola".equals(msg.content())));
        assertNotEquals("Dolor abdominal", response);
    }

    @Test
    void seguimientoTemporalPriorizaFactDeInicioYSinRepetirMotivo() {
        ClinicalCaseFact temporalFact = new ClinicalCaseFact(
                UUID.randomUUID(),
                clinicalCase.getId(),
                "GENERAL",
                "inicio_sintomas",
                "Comencé con tos seca hace 10 días",
                2,
                null,
                false,
                1
        );
        when(clinicalCaseFactRepository.findByCasoIdOrderByOrdenAsc(clinicalCase.getId()))
                .thenReturn(List.of(level1Fact, temporalFact, level3Fact));
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(sessionRevealedFactRepository.existsBySessionIdAndFactId(session.getId(), temporalFact.getId())).thenReturn(false);

        ChatMessage previousAssistant = new ChatMessage(
                UUID.randomUUID(),
                session.getId(),
                "ASSISTANT",
                "Dolor abdominal",
                2,
                LocalDateTime.now().minusSeconds(30)
        );
        ChatMessage previousUser = new ChatMessage(
                UUID.randomUUID(),
                session.getId(),
                "USER",
                "hola",
                1,
                LocalDateTime.now().minusMinutes(1)
        );
        when(chatMessageRepository.findBySesionIdOrderByNumeroTurnoDesc(any(), any()))
                .thenReturn(List.of(previousAssistant, previousUser));
        when(llmClient.generate(any())).thenReturn(new LlmResponse("Dolor abdominal", null, null));

        String response = service.generateResponse(session, "¿Hace cuánto se siente así?");

        assertEquals("Comencé con tos seca hace 10 días", response);
        assertNotEquals("Dolor abdominal", response);
    }

    @Test
    void registraMetricaEnExitoYFallback() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(llmClient.generate(any())).thenReturn(new LlmResponse("respuesta segura", null, null));

        service.generateResponse(session, "hola");

        ArgumentCaptor<LlmUsage> successCaptor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository, atLeastOnce()).save(successCaptor.capture());
        LlmUsage successUsage = successCaptor.getValue();
        assertFalse(readBooleanField(successUsage, "fallbackUsed"));
        assertEquals(null, readStringField(successUsage, "error"));

        reset(llmUsageRepository);
        when(llmUsageRepository.save(any(LlmUsage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("MODEL_INVALID"))
                .thenThrow(new LlmClientException("MODEL_INVALID"));

        service.generateResponse(session, "hola");

        ArgumentCaptor<LlmUsage> fallbackCaptor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository).save(fallbackCaptor.capture());
        LlmUsage fallbackUsage = fallbackCaptor.getValue();
        assertTrue(readBooleanField(fallbackUsage, "fallbackUsed"));
        assertTrue(readStringField(fallbackUsage, "error").contains("PROVIDER_CALL_ERROR"));
    }

    @Test
    void provider429RetornaFallbackLocalNoNulo() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("Error HTTP proveedor LLM status=429 detail=You exceeded your current quota type: insufficient_quota"));

        String response = service.generateResponse(session, "¿Qué siente ahora?");

        assertNotNull(response);
        assertFalse(response.isBlank());
        assertFalse(response.contains("Perdón, me cuesta responder"));
        verify(llmClient, times(2)).generate(any());

        ArgumentCaptor<LlmUsage> usageCaptor = ArgumentCaptor.forClass(LlmUsage.class);
        verify(llmUsageRepository, atLeastOnce()).save(usageCaptor.capture());
        LlmUsage usage = usageCaptor.getValue();
        assertTrue(readBooleanField(usage, "fallbackUsed"));
        assertTrue(readStringField(usage, "error").contains("status=429"));
    }

    @Test
    void multiplesTurnosConsecutivosCon429SiguenRespondiendoSinSilencio() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());
        when(llmClient.generate(any()))
                .thenThrow(new LlmClientException("Error HTTP proveedor LLM status=429 detail=insufficient_quota"));

        String responseTurno1 = service.generateResponse(session, "hola");
        String responseTurno2 = service.generateResponse(session, "¿Desde cuándo?");
        String responseTurno3 = service.generateResponse(session, "¿Tiene antecedentes?");

        assertTrue(responseTurno1 != null && !responseTurno1.isBlank());
        assertTrue(responseTurno2 != null && !responseTurno2.isBlank());
        assertTrue(responseTurno3 != null && !responseTurno3.isBlank());
        verify(llmClient, times(6)).generate(any());
    }

    private boolean readBooleanField(LlmUsage usage, String fieldName) {
        try {
            var field = LlmUsage.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getBoolean(usage);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private String readStringField(LlmUsage usage, String fieldName) {
        try {
            var field = LlmUsage.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (String) field.get(usage);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private String getLastContextualPrompt() {
        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient, atLeastOnce()).generate(requestCaptor.capture());
        return requestCaptor.getValue().messages().stream()
                .map(LlmMessage::content)
                .filter(content -> content.contains("Contexto clínico del caso:"))
                .findFirst()
                .orElse("");
    }

    private String getContextualPrompt() {
        return getLastContextualPrompt();
    }
}
