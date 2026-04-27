package cl.casesim.backend.sessions;

import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.common.exception.ConflictException;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import cl.casesim.backend.sessions.dto.ChatMessageResponse;
import cl.casesim.backend.sessions.dto.CreateChatMessageRequest;
import cl.casesim.backend.sessions.dto.CreateSessionRequest;
import cl.casesim.backend.sessions.dto.FinalDiagnosisRequest;
import cl.casesim.backend.sessions.dto.SessionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private static final String USER_ROLE = "USER";
    private static final String ASSISTANT_ROLE = "ASSISTANT";
    private static final String SESSION_IN_PROGRESS = "EN_CURSO";

    private final SimulationSessionRepository simulationSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PatientResponseService patientResponseService;

    public SessionService(
            SimulationSessionRepository simulationSessionRepository,
            ChatMessageRepository chatMessageRepository,
            PatientResponseService patientResponseService
    ) {
        this.simulationSessionRepository = simulationSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.patientResponseService = patientResponseService;
    }

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        LocalDateTime now = LocalDateTime.now();

        SimulationSession session = new SimulationSession(
                UUID.randomUUID(),
                request.activityId(),
                request.studentId(),
                SESSION_IN_PROGRESS,
                now,
                now
        );

        SimulationSession savedSession = simulationSessionRepository.save(session);
        return toSessionResponse(savedSession);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSessionById(UUID sessionId) {
        SimulationSession session = getSessionOrThrow(sessionId);
        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessagesBySession(UUID sessionId) {
        getSessionOrThrow(sessionId);

        return chatMessageRepository.findBySesionIdOrderByNumeroTurnoAsc(sessionId)
                .stream()
                .map(this::toChatMessageResponse)
                .toList();
    }

    @Transactional
    public List<ChatMessageResponse> createMessages(UUID sessionId, CreateChatMessageRequest request) {
        SimulationSession session = getSessionOrThrow(sessionId);
        assertSessionInProgressForMessages(session);

        if (request.content() == null || request.content().trim().isEmpty()) {
            throw new BadRequestException("El contenido del mensaje no puede estar vacío.");
        }

        int nextTurnNumber = chatMessageRepository.findMaxNumeroTurnoBySesionId(sessionId) + 1;

        ChatMessage userMessage = new ChatMessage(
                UUID.randomUUID(),
                sessionId,
                USER_ROLE,
                request.content().trim(),
                nextTurnNumber,
                LocalDateTime.now()
        );

        ChatMessage assistantMessage = new ChatMessage(
                UUID.randomUUID(),
                sessionId,
                ASSISTANT_ROLE,
                patientResponseService.generateResponse(session, request.content().trim()),
                nextTurnNumber + 1,
                LocalDateTime.now()
        );

        ChatMessage savedUserMessage = chatMessageRepository.save(userMessage);
        ChatMessage savedAssistantMessage = chatMessageRepository.save(assistantMessage);

        return List.of(toChatMessageResponse(savedUserMessage), toChatMessageResponse(savedAssistantMessage));
    }

    @Transactional
    public SessionResponse completeSession(UUID sessionId) {
        SimulationSession session = getSessionOrThrow(sessionId);
        assertSessionInProgress(session, "No se puede cerrar la sesión porque su estado actual es " + session.getEstado() + ".");

        session.completar(LocalDateTime.now());
        SimulationSession updatedSession = simulationSessionRepository.save(session);
        return toSessionResponse(updatedSession);
    }

    @Transactional
    public SessionResponse registerFinalDiagnosis(UUID sessionId, FinalDiagnosisRequest request) {
        SimulationSession session = getSessionOrThrow(sessionId);
        assertSessionInProgress(session, "No se puede registrar diagnóstico final porque la sesión está en estado " + session.getEstado() + ".");

        int turnoDiagnostico = chatMessageRepository.findMaxNumeroTurnoBySesionId(sessionId);

        session.registrarDiagnosticoFinal(
                request.diagnosis().trim(),
                request.reasoning().trim(),
                turnoDiagnostico,
                LocalDateTime.now()
        );

        SimulationSession updatedSession = simulationSessionRepository.save(session);
        return toSessionResponse(updatedSession);
    }

    private SimulationSession getSessionOrThrow(UUID sessionId) {
        return simulationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada con id: " + sessionId));
    }

    private SessionResponse toSessionResponse(SimulationSession session) {
        return new SessionResponse(
                session.getId(),
                session.getActividadId(),
                session.getEstudianteId(),
                session.getEstado(),
                session.getIniciadaEn(),
                session.getFinalizadaEn(),
                session.getCreadaEn()
        );
    }

    private ChatMessageResponse toChatMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getSesionId(),
                message.getRol(),
                message.getContenido(),
                message.getNumeroTurno(),
                message.getCreadoEn()
        );
    }

    private void assertSessionInProgressForMessages(SimulationSession session) {
        if (SESSION_IN_PROGRESS.equals(session.getEstado())) {
            return;
        }

        String message = switch (session.getEstado()) {
            case "PENDIENTE" -> "La sesión está PENDIENTE y aún no permite envío de mensajes.";
            case "FINALIZADA" -> "La sesión ya fue FINALIZADA y no acepta nuevos mensajes.";
            case "EXPIRADA" -> "La sesión está EXPIRADA y no acepta nuevos mensajes.";
            default -> "No se pueden enviar mensajes cuando la sesión está en estado " + session.getEstado() + ".";
        };

        throw new ConflictException(message);
    }

    private void assertSessionInProgress(SimulationSession session, String message) {
        if (!SESSION_IN_PROGRESS.equals(session.getEstado())) {
            throw new ConflictException(message);
        }
    }
}
