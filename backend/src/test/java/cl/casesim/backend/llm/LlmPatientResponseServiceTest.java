package cl.casesim.backend.llm;

import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseFact;
import cl.casesim.backend.clinicalcases.ClinicalCaseFactRepository;
import cl.casesim.backend.clinicalcases.ClinicalCasePersonalityRepository;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.sessions.ChatMessage;
import cl.casesim.backend.sessions.ChatMessageRepository;
import cl.casesim.backend.sessions.MockPatientResponseService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private final MockPatientResponseService mockPatientResponseService = new MockPatientResponseService();

    private final PromptBuilderService promptBuilderService = new PromptBuilderService();
    private final LlmUsageService llmUsageService = new LlmUsageService(
            llmUsageRepository,
            new BigDecimal("0.00015"),
            new BigDecimal("0.00060"),
            new BigDecimal("950")
    );

    private LlmPatientResponseService service;
    private SimulationSession session;
    private SimulationActivity activity;
    private ClinicalCase clinicalCase;
    private ClinicalCaseFact level1Fact;
    private ClinicalCaseFact level2Fact;
    private ClinicalCaseFact level3Fact;

    @BeforeEach
    void setUp() {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-api-key");

        service = new LlmPatientResponseService(
                properties,
                llmClient,
                promptBuilderService,
                responseSafetyFilter,
                chatMessageRepository,
                mockPatientResponseService,
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
        when(responseSafetyFilter.applyOrFallback(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(llmClient.generateChatCompletion(any())).thenReturn("respuesta segura");
    }

    @Test
    void seleccionInicialIncluyeSoloFactsNivelUno() {
        when(sessionRevealedFactRepository.findFactIdsBySessionId(session.getId())).thenReturn(Set.of());

        service.generateResponse(session, "Hola");

        String contextualPrompt = getContextualPrompt();
        assertTrue(contextualPrompt.contains("motivo: Dolor abdominal"));
        assertFalse(contextualPrompt.contains("fiebre: Tengo fiebre desde ayer"));
        assertFalse(contextualPrompt.contains("antecedente: Tuve cirugía hace 2 años"));
        verify(sessionRevealedFactRepository, never()).save(any(SessionRevealedFact.class));
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

    @SuppressWarnings("unchecked")
    private String getContextualPrompt() {
        ArgumentCaptor<List<LlmClient.ChatPromptMessage>> promptCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).generateChatCompletion(promptCaptor.capture());
        return promptCaptor.getValue().get(1).content();
    }
}
