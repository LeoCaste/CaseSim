package cl.casesim.backend.sessions;

import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import cl.casesim.backend.sessions.dto.ChatMessageResponse;
import cl.casesim.backend.sessions.dto.CreateChatMessageRequest;
import cl.casesim.backend.sessions.dto.CreateSessionRequest;
import cl.casesim.backend.sessions.dto.SimulationSessionResponse;
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
    private static final String MOCK_ASSISTANT_REPLY = "Entiendo. Cuénteme un poco más sobre eso.";

    private final SimulationSessionRepository simulationSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public SessionService(
            SimulationSessionRepository simulationSessionRepository,
            ChatMessageRepository chatMessageRepository
    ) {
        this.simulationSessionRepository = simulationSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public SimulationSessionResponse createSession(CreateSessionRequest request) {
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
    public SimulationSessionResponse getSessionById(UUID sessionId) {
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
        getSessionOrThrow(sessionId);

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
                MOCK_ASSISTANT_REPLY,
                nextTurnNumber + 1,
                LocalDateTime.now()
        );

        ChatMessage savedUserMessage = chatMessageRepository.save(userMessage);
        ChatMessage savedAssistantMessage = chatMessageRepository.save(assistantMessage);

        return List.of(toChatMessageResponse(savedUserMessage), toChatMessageResponse(savedAssistantMessage));
    }

    private SimulationSession getSessionOrThrow(UUID sessionId) {
        return simulationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada con id: " + sessionId));
    }

    private SimulationSessionResponse toSessionResponse(SimulationSession session) {
        return new SimulationSessionResponse(
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
}
