package cl.casesim.backend.sessions;

import cl.casesim.backend.common.exception.BadRequestException;
import cl.casesim.backend.common.exception.ConflictException;
import cl.casesim.backend.common.exception.ResourceNotFoundException;
import cl.casesim.backend.clinicalcases.ClinicalCase;
import cl.casesim.backend.clinicalcases.ClinicalCaseRepository;
import cl.casesim.backend.llm.ResponseSafetyFilter;
import cl.casesim.backend.simulations.SimulationActivity;
import cl.casesim.backend.simulations.SimulationActivityRepository;
import cl.casesim.backend.sessions.dto.ChatMessageResponse;
import cl.casesim.backend.sessions.dto.CreateSessionRequest;
import cl.casesim.backend.sessions.dto.FinalDiagnosisRequest;
import cl.casesim.backend.sessions.dto.SendMessageRequest;
import cl.casesim.backend.sessions.dto.SessionResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private static final String USER_ROLE = "USER";
    private static final String ASSISTANT_ROLE = "ASSISTANT";
    private static final String SESSION_IN_PROGRESS = "EN_CURSO";
    private static final String SESSION_PENDING = "PENDIENTE";
    private static final String SESSION_FINISHED = "FINALIZADA";
    private static final String SESSION_EXPIRED = "EXPIRADA";
    private static final String FINALIZED_SESSION_CONFLICT_MESSAGE = "Ya existe una sesión finalizada para esta actividad y estudiante. Use otra actividad o estudiante para una nueva sesión.";
    private static final String ASSISTANT_TECHNICAL_FALLBACK = "Perdón, me cuesta responder en este momento. ¿Podrías repetir tu pregunta?";

    private final SimulationSessionRepository simulationSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PatientResponseService patientResponseService;
    private final ResponseSafetyFilter responseSafetyFilter;
    private final SimulationActivityRepository simulationActivityRepository;
    private final ClinicalCaseRepository clinicalCaseRepository;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;

    public SessionService(
            SimulationSessionRepository simulationSessionRepository,
            ChatMessageRepository chatMessageRepository,
            PatientResponseService patientResponseService,
            ResponseSafetyFilter responseSafetyFilter,
            SimulationActivityRepository simulationActivityRepository,
            ClinicalCaseRepository clinicalCaseRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.simulationSessionRepository = simulationSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.patientResponseService = patientResponseService;
        this.responseSafetyFilter = responseSafetyFilter;
        this.simulationActivityRepository = simulationActivityRepository;
        this.clinicalCaseRepository = clinicalCaseRepository;

        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
    }

    @Transactional
    public CreateSessionResult createSession(CreateSessionRequest request, UUID authenticatedStudentId) {
        LocalDateTime now = LocalDateTime.now();

        SimulationSession existingSession = simulationSessionRepository
                .findByActividadIdAndEstudianteId(request.activityId(), authenticatedStudentId)
                .orElse(null);

        if (existingSession != null) {
            return resolveExistingSession(existingSession, now);
        }

        SimulationSession session = new SimulationSession(
                UUID.randomUUID(),
                request.activityId(),
                authenticatedStudentId,
                SESSION_IN_PROGRESS,
                now,
                now
        );

        try {
            SimulationSession savedSession = simulationSessionRepository.save(session);
            return new CreateSessionResult(toSessionResponse(savedSession), true);
        } catch (DataIntegrityViolationException ex) {
            SimulationSession persistedSession = simulationSessionRepository
                    .findByActividadIdAndEstudianteId(request.activityId(), authenticatedStudentId)
                    .orElseThrow(() -> ex);

            return resolveExistingSession(persistedSession, now);
        }
    }

    @Transactional(readOnly = true)
    public SessionResponse getSessionById(UUID sessionId, UUID authenticatedStudentId) {
        return toSessionResponse(getOwnedSessionOrThrow(sessionId, authenticatedStudentId));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessagesBySession(UUID sessionId, UUID authenticatedStudentId) {
        getOwnedSessionOrThrow(sessionId, authenticatedStudentId);

        return chatMessageRepository.findBySesionIdOrderByNumeroTurnoAsc(sessionId)
                .stream()
                .map(this::toChatMessageResponse)
                .toList();
    }

    public List<ChatMessageResponse> createMessages(UUID sessionId, SendMessageRequest request, UUID authenticatedStudentId) {
        SimulationSession session = readOnlyTransactionTemplate.execute(status -> {
            SimulationSession ownedSession = getOwnedSessionOrThrow(sessionId, authenticatedStudentId);
            assertSessionInProgressForMessages(ownedSession);
            return ownedSession;
        });

        String userContent = request.content() == null ? "" : request.content().trim();
        if (userContent.isEmpty()) {
            throw new BadRequestException("El contenido del mensaje no puede estar vacío.");
        }

        ChatMessage savedUserMessage = saveUserMessageInNewTransaction(sessionId, userContent, authenticatedStudentId);

        String assistantRawContent;
        try {
            assistantRawContent = patientResponseService.generateResponse(session, userContent);
        } catch (RuntimeException ex) {
            assistantRawContent = ASSISTANT_TECHNICAL_FALLBACK;
        }

        String assistantContent;
        try {
            assistantContent = responseSafetyFilter.applyOrFallback(assistantRawContent);
        } catch (RuntimeException ex) {
            assistantContent = ASSISTANT_TECHNICAL_FALLBACK;
        }

        ChatMessage savedAssistantMessage = saveAssistantMessageInNewTransaction(
                sessionId,
                assistantContent,
                authenticatedStudentId,
                savedUserMessage.getNumeroTurno() + 1
        );

        return List.of(toChatMessageResponse(savedUserMessage), toChatMessageResponse(savedAssistantMessage));
    }

    @Transactional
    public SessionResponse completeSession(UUID sessionId, UUID authenticatedStudentId) {
        SimulationSession session = getOwnedSessionOrThrow(sessionId, authenticatedStudentId);
        assertSessionInProgress(session, "No se puede cerrar la sesión porque su estado actual es " + session.getEstado() + ".");

        session.completar(LocalDateTime.now());
        return toSessionResponse(simulationSessionRepository.save(session));
    }

    @Transactional
    public SessionResponse registerFinalDiagnosis(UUID sessionId, FinalDiagnosisRequest request, UUID authenticatedStudentId) {
        SimulationSession session = getOwnedSessionOrThrow(sessionId, authenticatedStudentId);
        assertSessionInProgress(session, "No se puede registrar diagnóstico final porque la sesión está en estado " + session.getEstado() + ".");

        int turnoDiagnostico = chatMessageRepository.findMaxNumeroTurnoBySesionId(sessionId);
        session.registrarDiagnosticoFinal(
                request.diagnosis().trim(),
                request.reasoning().trim(),
                turnoDiagnostico,
                LocalDateTime.now()
        );

        return toSessionResponse(simulationSessionRepository.save(session));
    }

    private SimulationSession getOwnedSessionOrThrow(UUID sessionId, UUID authenticatedStudentId) {
        return simulationSessionRepository.findByIdAndEstudianteId(sessionId, authenticatedStudentId)
                .orElseThrow(() -> new ResourceNotFoundException("Sesión no encontrada con id: " + sessionId));
    }

    private ChatMessage saveUserMessageInNewTransaction(UUID sessionId, String userContent, UUID authenticatedStudentId) {
        return requiresNewTransactionTemplate.execute(status -> {
            SimulationSession session = getOwnedSessionOrThrow(sessionId, authenticatedStudentId);
            assertSessionInProgressForMessages(session);

            int nextTurnNumber = chatMessageRepository.findMaxNumeroTurnoBySesionId(sessionId) + 1;
            return chatMessageRepository.save(new ChatMessage(
                    UUID.randomUUID(),
                    sessionId,
                    USER_ROLE,
                    userContent,
                    nextTurnNumber,
                    LocalDateTime.now()
            ));
        });
    }

    private ChatMessage saveAssistantMessageInNewTransaction(
            UUID sessionId,
            String assistantContent,
            UUID authenticatedStudentId,
            int assistantTurnNumber
    ) {
        return requiresNewTransactionTemplate.execute(status -> {
            SimulationSession session = getOwnedSessionOrThrow(sessionId, authenticatedStudentId);
            assertSessionInProgressForMessages(session);

            return chatMessageRepository.save(new ChatMessage(
                    UUID.randomUUID(),
                    sessionId,
                    ASSISTANT_ROLE,
                    assistantContent,
                    assistantTurnNumber,
                    LocalDateTime.now()
            ));
        });
    }

    private SessionResponse toSessionResponse(SimulationSession session) {
        SimulationActivity activity = simulationActivityRepository.findById(session.getActividadId())
                .orElseThrow(() -> new ResourceNotFoundException("Actividad no encontrada con id: " + session.getActividadId()));

        ClinicalCase clinicalCase = clinicalCaseRepository.findById(activity.getCasoId())
                .orElseThrow(() -> new ResourceNotFoundException("Caso clínico no encontrado con id: " + activity.getCasoId()));

        return new SessionResponse(
                session.getId(),
                session.getActividadId(),
                clinicalCase.getId(),
                session.getEstudianteId(),
                session.getEstado(),
                session.getIniciadaEn(),
                session.getFinalizadaEn(),
                session.getCreadaEn(),
                session.getDiagnosticoFinal(),
                session.getRazonamientoFinal(),
                new SessionResponse.ClinicalCaseSummary(
                        clinicalCase.getPacienteNombre(),
                        clinicalCase.getPacienteEdad(),
                        clinicalCase.getPacienteSexo(),
                        clinicalCase.getMotivoConsulta()
                )
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

    private CreateSessionResult resolveExistingSession(SimulationSession existingSession, LocalDateTime now) {
        String estado = existingSession.getEstado();

        if (SESSION_IN_PROGRESS.equals(estado)) {
            return new CreateSessionResult(toSessionResponse(existingSession), false);
        }

        if (SESSION_PENDING.equals(estado)) {
            existingSession.iniciarEnCurso(now);
            SimulationSession updatedSession = simulationSessionRepository.save(existingSession);
            return new CreateSessionResult(toSessionResponse(updatedSession), false);
        }

        if (SESSION_FINISHED.equals(estado) || SESSION_EXPIRED.equals(estado)) {
            throw new ConflictException(FINALIZED_SESSION_CONFLICT_MESSAGE);
        }

        throw new ConflictException("No se puede crear una sesión para estado actual: " + estado + ".");
    }

    public record CreateSessionResult(SessionResponse session, boolean created) {
    }
}
