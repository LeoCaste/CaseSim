package cl.casesim.backend.sessions;

import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.llm.ResponseSafetyFilter;
import cl.casesim.backend.sessions.dto.ChatMessageResponse;
import cl.casesim.backend.sessions.dto.SendMessageRequest;
import cl.casesim.backend.sessions.dto.SessionResponse;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    private final SimulationSessionRepository simulationSessionRepository = mock(SimulationSessionRepository.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final PatientResponseService patientResponseService = mock(PatientResponseService.class);
    private final ResponseSafetyFilter responseSafetyFilter = mock(ResponseSafetyFilter.class);
    private final SimulationActivityRepository simulationActivityRepository = mock(SimulationActivityRepository.class);
    private final ClinicalCaseRepository clinicalCaseRepository = mock(ClinicalCaseRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private final SessionService sessionService = new SessionService(
            simulationSessionRepository,
            chatMessageRepository,
            patientResponseService,
            responseSafetyFilter,
            simulationActivityRepository,
            clinicalCaseRepository,
            transactionManager
    );

    SessionServiceTest() {
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
    }

    @Test
    void getSessionByIdRetornaDatosRealesDePacienteAsignado() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        SimulationSession session = new SimulationSession(
                sessionId,
                activityId,
                studentId,
                "EN_CURSO",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        SimulationActivity activity = new SimulationActivity(
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

        ClinicalCase clinicalCase = new ClinicalCase(
                caseId,
                "Caso demo",
                "Descripción",
                "Catalina Paz Soto",
                22,
                "Femenino",
                "Vengo porque tengo una tos seca que no se me pasa y me siento muy agotada.",
                "No tengo información asociada a eso.",
                true,
                UUID.randomUUID(),
                LocalDateTime.now()
        );

        when(simulationSessionRepository.findByIdAndEstudianteId(sessionId, studentId)).thenReturn(Optional.of(session));
        when(simulationActivityRepository.findById(activityId)).thenReturn(Optional.of(activity));
        when(clinicalCaseRepository.findById(caseId)).thenReturn(Optional.of(clinicalCase));

        SessionResponse response = sessionService.getSessionById(sessionId, studentId);

        assertEquals(caseId, response.clinicalCaseId());
        assertEquals("Catalina Paz Soto", response.clinicalCase().patientName());
        assertEquals(22, response.clinicalCase().patientAge());
        assertEquals("Femenino", response.clinicalCase().patientSex());
        assertEquals("Vengo porque tengo una tos seca que no se me pasa y me siento muy agotada.", response.clinicalCase().consultationReason());
    }

    @Test
    void createMessagesPersisteAssistantCuandoSeUsaFallbackPorErrorDelProveedor() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        SimulationSession session = new SimulationSession(
                sessionId,
                UUID.randomUUID(),
                studentId,
                "EN_CURSO",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(simulationSessionRepository.findByIdAndEstudianteId(sessionId, studentId)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMaxNumeroTurnoBySesionId(sessionId)).thenReturn(0);
        when(patientResponseService.generateResponse(eq(session), eq("hola"))).thenReturn("respuesta paciente");
        when(responseSafetyFilter.applyOrFallback("respuesta paciente")).thenReturn("respuesta paciente");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ChatMessageResponse> response = sessionService.createMessages(sessionId, new SendMessageRequest("hola"), studentId);

        assertEquals(2, response.size());
        assertEquals("USER", response.get(0).role());
        assertEquals("ASSISTANT", response.get(1).role());
        assertEquals("respuesta paciente", response.get(1).content());
    }

    @Test
    void createMessagesPropagaErrorSiPatientResponseExplota() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        SimulationSession session = new SimulationSession(
                sessionId,
                UUID.randomUUID(),
                studentId,
                "EN_CURSO",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(simulationSessionRepository.findByIdAndEstudianteId(sessionId, studentId)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMaxNumeroTurnoBySesionId(sessionId)).thenReturn(0);
        when(patientResponseService.generateResponse(eq(session), eq("hola"))).thenThrow(new RuntimeException("llm down"));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(RuntimeException.class, () -> sessionService.createMessages(sessionId, new SendMessageRequest("hola"), studentId));

        ArgumentCaptor<ChatMessage> savedCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(1)).save(savedCaptor.capture());
        assertEquals("USER", savedCaptor.getAllValues().getFirst().getRol());
    }

    @Test
    void createMessagesPersisteAssistantAunqueFalleSafetyFilter() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        SimulationSession session = new SimulationSession(
                sessionId,
                UUID.randomUUID(),
                studentId,
                "EN_CURSO",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(simulationSessionRepository.findByIdAndEstudianteId(sessionId, studentId)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findMaxNumeroTurnoBySesionId(sessionId)).thenReturn(2);
        when(patientResponseService.generateResponse(eq(session), eq("desde cuando"))).thenReturn("respuesta paciente");
        when(responseSafetyFilter.applyOrFallback("respuesta paciente")).thenThrow(new RuntimeException("safety down"));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ChatMessageResponse> response = sessionService.createMessages(sessionId, new SendMessageRequest("desde cuando"), studentId);

        assertEquals(2, response.size());
        assertEquals("ASSISTANT", response.get(1).role());
        assertTrue(response.get(1).content().contains("Perdón, me cuesta responder"));

        ArgumentCaptor<ChatMessage> savedCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(savedCaptor.capture());
        assertEquals("ASSISTANT", savedCaptor.getAllValues().get(1).getRol());
        assertEquals("EN_CURSO", session.getEstado());
    }
}
